package app.revanced.tiktok.patches;

import app.revanced.patcher.patch.Patch;
import app.revanced.patcher.patch.PatchKt;
import app.revanced.patcher.extensions.InstructionExtensions;
import app.revanced.patcher.extensions.ExternalLabel;
import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import java.util.ArrayList;
import kotlin.Unit;

/** Scope permission changes to download records, preserving other share actions. */
public final class EnableDownloadsPatch {
    private static final String MODEL = "Lcom/ss/android/ugc/aweme/feed/model/";
    private static final String ACL = MODEL + "ACLCommonShare;";
    private static final String OWNER = MODEL + "AwemeACLShare;";
    private static final String VIDEO = MODEL + "Video;";
    private static final String URL = "Lcom/ss/android/ugc/aweme/base/model/UrlModel;";
    private static final String SELECTOR = "LX/19k8;";

    public static final Patch enableDownloadsPatch = PatchKt.bytecodePatch(
        "Enable downloads", "Enables saving videos and photos whose download permission disables or hides the action.", false, builder -> {
            builder.compatibleWith(builder.invoke("com.zhiliaoapp.musically", "47.1.4"),
                builder.invoke("com.zhiliaoapp.musically.filtered", "47.1.4"));
            builder.apply(context -> {
                ClassDef permissions = context.getClassDefs().get(ACL);
                ClassDef owner = context.getClassDefs().get(OWNER);
                if (permissions == null || owner == null) throw new IllegalStateException("Download permission models absent");
                for (String field : new String[]{"code", "showType"}) {
                    boolean found = false;
                    for (var candidate : permissions.getFields()) {
                        if (candidate.getName().equals(field) && candidate.getType().equals("I")
                            && AccessFlags.PUBLIC.isSet(candidate.getAccessFlags()) && !AccessFlags.STATIC.isSet(candidate.getAccessFlags())) found = true;
                    }
                    if (!found) throw new IllegalStateException("Download permission field absent: " + field);
                }
                for (String field : new String[]{"downloadGeneral", "downloadMaskPanel", "downloadSharePanel"}) {
                    String name = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
                    Method original = null;
                    for (var method : owner.getMethods()) {
                        if (method.getName().equals(name) && method.getParameterTypes().isEmpty() && method.getReturnType().equals(ACL)
                            && !AccessFlags.STATIC.isSet(method.getAccessFlags()) && method.getImplementation() != null) original = method;
                    }
                    if (original == null) throw new IllegalStateException("Download permission getter absent: " + name);
                    var instructions = new ArrayList<com.android.tools.smali.dexlib2.iface.instruction.Instruction>();
                    original.getImplementation().getInstructions().forEach(instructions::add);
                    if (original.getImplementation().getRegisterCount() != 2 || instructions.size() != 2
                        || instructions.get(0).getOpcode() != Opcode.IGET_OBJECT || instructions.get(1).getOpcode() != Opcode.RETURN_OBJECT
                        || ((TwoRegisterInstruction) instructions.get(0)).getRegisterA() != 0
                        || ((TwoRegisterInstruction) instructions.get(0)).getRegisterB() != 1
                        || ((OneRegisterInstruction) instructions.get(1)).getRegisterA() != 0
                        || !(((ReferenceInstruction) instructions.get(0)).getReference() instanceof FieldReference)
                        || !((ReferenceInstruction) instructions.get(0)).getReference().toString().equals(OWNER + "->" + field + ":" + ACL)) {
                        throw new IllegalStateException("Unexpected download getter shape: " + name);
                    }
                    for (var method : context.proxy(owner).getMutableClass().getMethods()) {
                        if (!method.getName().equals(name) || !method.getParameterTypes().isEmpty() || !method.getReturnType().equals(ACL)) continue;
                        // The verified getter has already read its receiver into v0.
                        // v1 (p0) is dead at its return and can hold integer values.
                        // A missing record remains null for TikTok's native fallback.
                        var unchanged = new ExternalLabel("unchanged", method.getImplementation().getInstructions().get(1));
                        InstructionExtensions.INSTANCE.addInstructionsWithLabels(method, 1,
                            "if-eqz v0, :unchanged\n"
                            + "const/4 v1, 0x0\n"
                            + "iput v1, v0, " + ACL + "->code:I\n"
                            + "const/4 v1, 0x2\n"
                            + "iput v1, v0, " + ACL + "->showType:I", unchanged);
                        break;
                    }
                }
                // This native downloader can return no source when the dedicated
                // download URL is absent and its experiment flag disables fallback.
                // Reuse the playback source, then let native cache keys and saving run.
                ClassDef selector = context.getClassDefs().get(SELECTOR);
                if (selector == null) throw new IllegalStateException("Native video download selector absent");
                Method source = null;
                for (var method : selector.getMethods()) {
                    if (method.getName().equals("LIZ") && method.getReturnType().equals("V")
                        && method.getParameterTypes().equals(java.util.List.of(MODEL + "Aweme;", "Z"))
                        && !AccessFlags.STATIC.isSet(method.getAccessFlags()) && method.getImplementation() != null) source = method;
                }
                if (source == null || source.getImplementation().getRegisterCount() != 7)
                    throw new IllegalStateException("Unexpected native download selector shape");
                var sourceInstructions = new ArrayList<com.android.tools.smali.dexlib2.iface.instruction.Instruction>();
                source.getImplementation().getInstructions().forEach(sourceInstructions::add);
                int insertion = -1;
                for (int i = 0; i < sourceInstructions.size(); i++) {
                    var instruction = sourceInstructions.get(i);
                    if (!(instruction instanceof ReferenceInstruction)
                        || !((ReferenceInstruction) instruction).getReference().toString().equals(VIDEO + "->getDownloadNoWatermarkAddr()" + URL)) continue;
                    if (insertion != -1 || instruction.getOpcode() != Opcode.INVOKE_VIRTUAL
                        || ((FiveRegisterInstruction) instruction).getRegisterCount() != 1
                        || ((FiveRegisterInstruction) instruction).getRegisterC() != 0
                        || i + 2 >= sourceInstructions.size()
                        || sourceInstructions.get(i + 1).getOpcode() != Opcode.MOVE_RESULT_OBJECT
                        || ((OneRegisterInstruction) sourceInstructions.get(i + 1)).getRegisterA() != 1
                        || sourceInstructions.get(i + 2).getOpcode() != Opcode.IF_NEZ
                        || ((OneRegisterInstruction) sourceInstructions.get(i + 2)).getRegisterA() != 1)
                        throw new IllegalStateException("Unexpected native download URL lookup");
                    insertion = i + 2;
                }
                if (insertion == -1) throw new IllegalStateException("Native download URL lookup absent");
                for (var method : context.proxy(selector).getMutableClass().getMethods()) {
                    if (!method.getName().equals(source.getName()) || !method.getParameterTypes().equals(source.getParameterTypes())) continue;
                    var selected = new ExternalLabel("selected", method.getImplementation().getInstructions().get(insertion));
                    InstructionExtensions.INSTANCE.addInstructionsWithLabels(method, insertion,
                        "if-nez v1, :selected\n"
                        + "invoke-virtual {v0}, " + VIDEO + "->getPlayAddrH264()" + MODEL + "VideoUrlModel;\n"
                        + "move-result-object v1", selected);
                    break;
                }
                System.out.println("Enabled download records and missing video source fallback; unrelated sharing permissions preserved");
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        }
    );
}
