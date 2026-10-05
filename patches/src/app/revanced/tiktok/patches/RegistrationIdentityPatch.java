package app.revanced.tiktok.patches;

import app.revanced.patcher.patch.Patch;
import app.revanced.patcher.patch.PatchKt;
import app.revanced.patcher.extensions.InstructionExtensions;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import java.util.List;
import kotlin.Unit;

/** Exact AppLog collector verified in the supplied 47.1.4 APK and TLS capture. */
public final class RegistrationIdentityPatch {
    private static final String JSON = "Lorg/json/JSONObject;";
    private static final String PUT = JSON + "->put(Ljava/lang/String;Ljava/lang/Object;)" + JSON;
    private static final String HELPER = "Lapp/revanced/tiktok/RegistrationIdentity;->put(" + JSON
        + "Ljava/lang/String;Ljava/lang/Object;)" + JSON;

    public static final Patch fixDeviceRegistrationPatch = PatchKt.bytecodePatch(
        "Fix device registration",
        "Fixes device registration for the filtered package name without requiring the original app.",
        false, builder -> {
            builder.compatibleWith(builder.invoke("com.zhiliaoapp.musically", "47.1.4"));
            builder.compatibleWith(builder.invoke("com.zhiliaoapp.musically.filtered", "47.1.4"));
            builder.dependsOn(TikTokPatches.EXTENSION);
            builder.apply(context -> {
                var cls = context.getClassDefs().get("LX/08An;");
                if (cls == null) throw new IllegalStateException("Missing verified AppLog package collector");
                int changed = 0;
                for (var method : context.proxy(cls).getMutableClass().getMethods()) {
                    if (!method.getName().equals("LIZ") || !method.getReturnType().equals("Z")
                        || !method.getParameterTypes().equals(List.of(JSON)) || method.getImplementation() == null) continue;
                    var instructions = method.getImplementation().getInstructions();
                    for (int i = 0; i < instructions.size(); i++) {
                        var instruction = instructions.get(i);
                        if (!(instruction instanceof ReferenceInstruction)
                            || !((ReferenceInstruction) instruction).getReference().toString().equals(PUT)) continue;
                        if (instruction.getOpcode() != Opcode.INVOKE_VIRTUAL
                            || !(instruction instanceof FiveRegisterInstruction))
                            throw new IllegalStateException("AppLog object-put invocation layout changed");
                        var registers = (FiveRegisterInstruction) instruction;
                        if (registers.getRegisterCount() != 3) throw new IllegalStateException("Unexpected AppLog put arguments");
                        InstructionExtensions.INSTANCE.replaceInstruction(method, i,
                            "invoke-static {v" + registers.getRegisterC() + ", v" + registers.getRegisterD()
                            + ", v" + registers.getRegisterE() + "}, " + HELPER);
                        changed++;
                    }
                }
                if (changed != 8) throw new IllegalStateException("Expected eight verified AppLog object writes, found " + changed);
                System.out.println("Registration identity: eight scoped AppLog object writes");
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });
}
