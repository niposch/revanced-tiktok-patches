package app.revanced.tiktok.patches;

import app.revanced.patcher.patch.Patch;
import app.revanced.patcher.patch.PatchKt;
import app.revanced.patcher.extensions.InstructionExtensions;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashSet;
import java.util.regex.Pattern;
import kotlin.Unit;
import org.w3c.dom.Element;

/** Optional package change; content filters remain independently selectable. */
public final class AlongsidePatch {
    private static final String OLD = "com.zhiliaoapp.musically";
    private static final String NEW = "com.zhiliaoapp.musically.filtered";
    private static final Map<String, String> STRINGS = new LinkedHashMap<>();

    private static final Patch MANIFEST = PatchKt.resourcePatch(null, null, true, builder -> {
        builder.apply(context -> {
            STRINGS.clear();
            STRINGS.put(OLD, NEW);
            try (var document = context.document("AndroidManifest.xml")) {
                Element manifest = document.getDocumentElement();
                if (!manifest.getAttribute("package").equals(OLD)) throw new IllegalStateException("Unexpected package");
                manifest.setAttribute("package", NEW);
                var nodes = document.getElementsByTagName("*");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element node = (Element) nodes.item(i);
                    String tag = node.getTagName();
                    String name = node.getAttribute("android:name");
                    // This standalone APK retains a dangling Play split resource
                    // reference; it is irrelevant after installing the fused APK.
                    if (tag.equals("meta-data") && name.equals("com.android.vending.splits")) {
                        node.getParentNode().removeChild(node);
                        i--;
                        continue;
                    }
                    boolean component = tag.equals("application") || tag.equals("activity")
                        || tag.equals("activity-alias") || tag.equals("service") || tag.equals("receiver") || tag.equals("provider");
                    if (component && !name.isEmpty() && !name.contains(".")) node.setAttribute("android:name", OLD + "." + name);
                    if (component && name.startsWith(".")) node.setAttribute("android:name", OLD + name);
                    if ((tag.equals("permission") || tag.startsWith("uses-permission")) && name.startsWith(OLD + ".")) {
                        replace(node, "name");
                    }
                    for (String attr : new String[]{"authorities", "permission", "readPermission", "writePermission", "taskAffinity", "process"}) {
                        replace(node, attr);
                    }
                    if (tag.equals("application")) node.setAttribute("android:label", "TikTok Filtered");
                    if (tag.equals("activity-alias")) {
                        String target = node.getAttribute("android:targetActivity");
                        if (target.startsWith(".")) node.setAttribute("android:targetActivity", OLD + target);
                    }
                }
            } catch (Exception exception) { throw new IllegalStateException("Could not create alongside manifest", exception); }
            // TikTok ships thousands of zero-byte PNG placeholders. Android accepts
            // the original APK, but AAPT2 cannot recompile empty images. Preserve
            // their names and resource IDs with a transparent one-pixel placeholder.
            try (var paths = Files.walk(context.get("res", false).toPath())) {
                byte[] transparent = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAXpeqz8AAAAASUVORK5CYII=");
                int fixed = 0;
                for (var path : (Iterable<java.nio.file.Path>) paths::iterator) {
                    if (path.toString().endsWith(".png") && Files.isRegularFile(path) && Files.size(path) == 0) {
                        Files.write(path, transparent);
                        fixed++;
                    }
                }
                System.out.println("Made " + fixed + " empty image placeholders compilable");
            } catch (Exception exception) { throw new IllegalStateException("Could not normalize empty resources", exception); }
            // APKTool loses this obfuscated string reference while decoding
            // 47.1.4. Without it the shared bottom-sheet dialog throws during
            // setContentView, then displays an empty window that captures input.
            // Use the class name verified against the original app's resources.
            try (var document = context.document("res/layout/bl_.xml")) {
                var nodes = document.getElementsByTagName("FrameLayout");
                int restored = 0;
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element node = (Element) nodes.item(i);
                    // The document reader retains qualified names but does not
                    // expose their namespace URI. APKTool may choose any prefix.
                    boolean container = false;
                    var named = node.getAttributes();
                    for (int j = 0; j < named.getLength(); j++) {
                        var attribute = named.item(j);
                        if (attribute.getNodeName().endsWith(":id") && attribute.getNodeValue().equals("@id/g3d")) container = true;
                    }
                    if (!container) continue;
                    String behavior = node.getAttribute("c4g");
                    String expected = "com.google.android.material.bottomsheet.BottomSheetBehavior";
                    if (!behavior.equals("@null") && !behavior.equals(expected)) {
                        throw new IllegalStateException("Unexpected bottom-sheet behavior declaration");
                    }
                    node.setAttribute("c4g", expected);
                    restored++;
                }
                if (restored != 1) throw new IllegalStateException("Expected one shared bottom-sheet container");
            } catch (Exception exception) { throw new IllegalStateException("Could not restore bottom-sheet behavior", exception); }
            // APKTool decodes TikTok's namespace-less, obfuscated custom attributes
            // as bare names. AAPT2 then drops their resource IDs. Reattach a proper
            // custom namespace so obtainStyledAttributes keeps the original IDs.
            try {
                var attributes = new HashSet<String>();
                try (var document = context.document("res/values/attrs.xml")) {
                    var nodes = document.getElementsByTagName("attr");
                    for (int i = 0; i < nodes.getLength(); i++) attributes.add(((Element) nodes.item(i)).getAttribute("name"));
                }
                var attributePattern = Pattern.compile("(?<=\\s)([A-Za-z_][A-Za-z0-9_.-]*)=(?=[\"'])");
                var rootPattern = Pattern.compile("<(?![!?/])[^\\s>]+");
                int fixed = 0;
                try (var paths = Files.walk(context.get("res", false).toPath())) {
                    for (var path : (Iterable<java.nio.file.Path>) paths::iterator) {
                        if (!path.toString().endsWith(".xml") || path.getParent().getFileName().toString().startsWith("values")) continue;
                        String text = Files.readString(path);
                        var matcher = attributePattern.matcher(text);
                        var rewritten = new StringBuffer();
                        boolean changed = false;
                        while (matcher.find()) {
                            if (!attributes.contains(matcher.group(1))) continue;
                            matcher.appendReplacement(rewritten, "revancedCustom:" + matcher.group(1) + "=");
                            changed = true;
                        }
                        if (!changed) continue;
                        matcher.appendTail(rewritten);
                        var root = rootPattern.matcher(rewritten);
                        if (!root.find()) throw new IllegalStateException("No resource XML root: " + path);
                        rewritten.insert(root.end(), " xmlns:revancedCustom=\"http://schemas.android.com/apk/res-auto\"");
                        Files.writeString(path, rewritten);
                        fixed++;
                    }
                }
                System.out.println("Restored custom attribute namespaces in " + fixed + " resource XML files");
            } catch (Exception exception) { throw new IllegalStateException("Could not preserve custom attribute IDs", exception); }
            return Unit.INSTANCE;
        });
        return Unit.INSTANCE;
    });

    public static final Patch changePackageNamePatch = PatchKt.bytecodePatch(
        "Change package name", "Changes the package to com.zhiliaoapp.musically.filtered and the app name to TikTok Filtered.", false, builder -> {
            builder.compatibleWith(builder.invoke(OLD, "47.1.4"));
            builder.dependsOn(MANIFEST);
            builder.dependsOn(RegistrationIdentityPatch.fixDeviceRegistrationPatch);
            builder.apply(context -> {
                int changed = 0;
                for (var cls : new ArrayList<>(context.getClassDefs())) {
                    // Helpers deliberately retain the original package for identity
                    // lookups. Only TikTok's own package/authority literals change.
                    if (cls.getType().startsWith("Lapp/revanced/tiktok/")) continue;
                    for (var original : cls.getMethods()) {
                        if (original.getImplementation() == null) continue;
                        var replacements = new LinkedHashMap<Integer, String>();
                        int index = 0;
                        for (var instruction : original.getImplementation().getInstructions()) {
                            if ((instruction.getOpcode() == Opcode.CONST_STRING || instruction.getOpcode() == Opcode.CONST_STRING_JUMBO)
                                    && ((ReferenceInstruction) instruction).getReference() instanceof StringReference) {
                                String value = ((StringReference) ((ReferenceInstruction) instruction).getReference()).getString();
                                String replacement = STRINGS.get(value);
                                if (replacement != null) replacements.put(index, replacement);
                            }
                            index++;
                        }
                        if (replacements.isEmpty()) continue;
                        for (var method : context.proxy(cls).getMutableClass().getMethods()) {
                            if (!method.getName().equals(original.getName()) || !method.getParameterTypes().equals(original.getParameterTypes())
                                    || !method.getReturnType().equals(original.getReturnType())) continue;
                            for (var entry : replacements.entrySet()) {
                                int reg = ((OneRegisterInstruction) method.getImplementation().getInstructions().get(entry.getKey())).getRegisterA();
                                InstructionExtensions.INSTANCE.replaceInstruction(method, entry.getKey(),
                                    "const-string/jumbo v" + reg + ", \"" + entry.getValue() + "\"");
                                changed++;
                            }
                            break;
                        }
                    }
                }
                System.out.println("Alongside package: " + NEW + ", updated " + changed + " package/authority strings");
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        }
    );

    private static void replace(Element node, String attribute) {
        String value = node.getAttribute("android:" + attribute);
        if (value.isEmpty()) return;
        String replaced;
        if (attribute.equals("authorities")) {
            // Every registered authority must be unique, including Facebook and
            // ByteDance providers whose authority does not start with the package.
            if (node.getParentNode().getNodeName().equals("queries")) return;
            var authorities = new ArrayList<String>();
            for (String authority : value.split(";")) {
                String renamed = authority.contains(OLD) ? authority.replace(OLD, NEW) : authority + ".filtered";
                authorities.add(renamed);
                STRINGS.put(authority, renamed);
            }
            replaced = String.join(";", authorities);
        } else {
            if (!value.contains(OLD)) return;
            replaced = value.replace(OLD, NEW);
        }
        node.setAttribute("android:" + attribute, replaced);
        STRINGS.put(value, replaced);
    }
}
