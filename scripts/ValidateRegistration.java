import app.revanced.patcher.patch.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Execute the patch on the actual APK collector and verify unchanged arguments. */
public final class ValidateRegistration {
    private static final String TARGET = "LX/08An;";
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Method collector(ClassDef cls) {
        for (Method method : cls.getMethods()) if (method.getName().equals("LIZ")) return method;
        throw new AssertionError("Collector absent");
    }
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[2]);
        Files.createDirectories(dir);
        ClassDef original = null;
        try (ZipFile zip = new ZipFile(args[0])) {
            var entry = zip.getEntry("classes5.dex");
            var dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), new BufferedInputStream(zip.getInputStream(entry)));
            for (ClassDef cls : dex.getClasses()) if (cls.getType().equals(TARGET)) original = cls;
        }
        check(original != null, "Actual collector absent");
        var pool = new DexPool(Opcodes.getDefault());
        pool.internClass(original);
        var store = new com.android.tools.smali.dexlib2.writer.io.FileDataStore(dir.resolve("classes.dex").toFile());
        try { pool.writeTo(store); } finally { store.close(); }
        File fixture = dir.resolve("fixture.apk").toFile();
        try (var zip = new ZipOutputStream(new FileOutputStream(fixture))) {
            zip.putNextEntry(new ZipEntry("classes.dex"));
            Files.copy(dir.resolve("classes.dex"), zip);
            zip.closeEntry();
        }
        try (var loader = new URLClassLoader(new URL[]{new File(args[1]).toURI().toURL()}, ValidateRegistration.class.getClassLoader())) {
            var patch = (Patch) loader.loadClass("app.revanced.tiktok.patches.RegistrationIdentityPatch").getField("fixDeviceRegistrationPatch").get(null);
            var context = new BytecodePatchContext(fixture, dir.resolve("patched").toFile());
            var resources = new ResourcePatchContext(new File(args[0]), dir.resolve("resources").toFile(), dir.resolve("resources-out").toFile(), null, null);
            context.getClassDefs().initializeCache$patcher();
            for (Patch dependency : patch.getDependencies()) dependency.getApply$patcher().invoke(context, resources);
            patch.getApply$patcher().invoke(context, resources);
            Method before = collector(original), after = collector(context.getClassDefs().get(TARGET));
            check(before.getImplementation().getRegisterCount() == after.getImplementation().getRegisterCount(), "Registers changed");
            List<Instruction> a = new ArrayList<>(), b = new ArrayList<>();
            before.getImplementation().getInstructions().forEach(a::add);
            after.getImplementation().getInstructions().forEach(b::add);
            check(a.size() == b.size(), "Instruction positions changed");
            int replacements = 0;
            for (int i = 0; i < a.size(); i++) {
                var left = a.get(i); var right = b.get(i);
                check(left.getCodeUnits() == right.getCodeUnits(), "Branch offsets changed");
                String ref = right instanceof ReferenceInstruction ? ((ReferenceInstruction) right).getReference().toString() : "";
                if (ref.startsWith("Lapp/revanced/tiktok/RegistrationIdentity;->put(")) {
                    check(left.getOpcode() == Opcode.INVOKE_VIRTUAL && right.getOpcode() == Opcode.INVOKE_STATIC, "Incorrect invocation type");
                    var from = (FiveRegisterInstruction) left; var to = (FiveRegisterInstruction) right;
                    check(from.getRegisterCount() == 3 && to.getRegisterCount() == 3, "Incorrect argument count");
                    check(from.getRegisterC() == to.getRegisterC() && from.getRegisterD() == to.getRegisterD()
                        && from.getRegisterE() == to.getRegisterE(), "JSON/key/value argument order changed");
                    replacements++;
                } else {
                    check(left.getOpcode() == right.getOpcode(), "Unrelated instruction changed");
                    if (left instanceof ReferenceInstruction)
                        check(((ReferenceInstruction) left).getReference().toString().equals(ref), "Unrelated reference changed");
                }
            }
            check(replacements == 8, "Incomplete collector coverage");
            check(context.getClassDefs().get("Lorg/json/JSONObject;") == null, "JVM fixture leaked into extension");
            check(context.getClassDefs().get("Lapp/revanced/tiktok/RegistrationIdentity;") != null, "Runtime helper absent");
            System.out.println("Actual APK registration patch: arguments, branches, metadata and fixture exclusion verified");
        }
    }
}
