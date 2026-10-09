import java.io.*;
import java.util.*;
import java.util.zip.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;

public final class ValidateExtension {
    public static void main(String[] args) throws Exception {
        Map<String, ClassDef> app = new HashMap<>();
        try (ZipFile zip = new ZipFile(args[0])) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                var dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), new BufferedInputStream(zip.getInputStream(entry)));
                for (ClassDef cls : dex.getClasses()) if (cls.getType().startsWith("Lcom/ss/android/ugc/aweme/feed/model/")
                    || cls.getType().equals("Lcom/ss/android/ugc/aweme/commerce/AwemeCommerceStruct;")) app.put(cls.getType(), cls);
            }
        }
        Set<String> checked = new TreeSet<>();
        try (ZipFile zip = new ZipFile(args[1])) {
            var dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(), new BufferedInputStream(zip.getInputStream(zip.getEntry("extensions/tiktok.rve"))));
            for (ClassDef cls : dex.getClasses()) {
                if (!cls.getType().startsWith("Lapp/revanced/tiktok/")) throw new IllegalStateException("Stub leaked into extension: " + cls.getType());
                for (Method method : cls.getMethods()) {
                    if (method.getImplementation() == null) continue;
                    for (Instruction ins : method.getImplementation().getInstructions()) {
                        if (!(ins instanceof ReferenceInstruction)) continue;
                        Reference ref = ((ReferenceInstruction) ins).getReference();
                        String owner = ref instanceof MethodReference ? ((MethodReference) ref).getDefiningClass()
                            : ref instanceof FieldReference ? ((FieldReference) ref).getDefiningClass() : "";
                        if (!owner.startsWith("Lcom/ss/android/ugc/aweme/")) continue;
                        ClassDef target = app.get(owner);
                        if (target == null) throw new IllegalStateException("Missing class: " + owner);
                        boolean found = false;
                        if (ref instanceof MethodReference) {
                            for (Method m : target.getMethods()) if (m.toString().equals(ref.toString()) && AccessFlags.PUBLIC.isSet(m.getAccessFlags())
                                    && !AccessFlags.STATIC.isSet(m.getAccessFlags())) found = true;
                        } else {
                            for (Field f : target.getFields()) if (f.toString().equals(ref.toString()) && AccessFlags.PUBLIC.isSet(f.getAccessFlags())
                                    && !AccessFlags.STATIC.isSet(f.getAccessFlags())) found = true;
                        }
                        if (!found) throw new IllegalStateException("Missing public instance member: " + ref);
                        checked.add(ref.toString());
                    }
                }
            }
        }
        System.out.println("Extension ABI verified against APK: " + checked.size() + " members; no model stubs packaged.");
        checked.forEach(System.out::println);
    }
}
