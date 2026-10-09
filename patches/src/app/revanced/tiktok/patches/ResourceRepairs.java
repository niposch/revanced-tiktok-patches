package app.revanced.tiktok.patches;

import app.revanced.patcher.patch.ResourcePatchContext;
import brut.androlib.res.data.ResTable;
import brut.androlib.res.data.value.ResFileValue;
import brut.androlib.res.decoder.ARSCDecoder;
import brut.androlib.res.decoder.AXmlResourceParser;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Element;
import org.xmlpull.v1.XmlPullParser;

/** Restore verified behavior references from the input APK, without guessing from null. */
final class ResourceRepairs {
    private static final int BEHAVIOR = 0x7f060eed;
    private static final Map<Integer, String> CLASSES = new HashMap<>();
    static {
        CLASSES.put(0x7f111ebf, "com.google.android.material.bottomsheet.BottomSheetBehavior");
        CLASSES.put(0x7f1119be, "com.google.android.material.appbar.AppBarLayout$ScrollingViewBehavior");
    }

    static void restoreBehaviors(ResourcePatchContext context) throws Exception {
        // Read the original table only to map decoded layout names to their original
        // binary files. No guessed layout list and no private patcher fields are used.
        var tableFile = context.get("resources.arsc", false).toPath();
        boolean tableCopied = !Files.exists(tableFile);
        ResTable table = new ResTable();
        Map<String, String> originals = new HashMap<>();
        try {
            try (var input = Files.newInputStream(context.get("resources.arsc", true).toPath())) {
                var data = new ARSCDecoder(input, table, false, false).decode();
                for (var pkg : data.getPackages()) {
                    table.addPackage(pkg, pkg.getId() == 0x7f);
                    for (var resource : pkg.listFiles()) {
                        if (!resource.getResSpec().getType().getName().equals("layout")) continue;
                        String decoded = "res/" + resource.getFilePath() + ".xml";
                        String original = "res/" + ((ResFileValue) resource.getValue()).getStrippedPath();
                        if (originals.put(decoded, original) != null) {
                            throw new IllegalStateException("Duplicate layout mapping: " + decoded);
                        }
                    }
                }
            }
        } finally {
            // A raw table left in the working directory could override the compiled
            // table when the patcher packages other files. Remove only our own copy.
            if (tableCopied) Files.deleteIfExists(tableFile);
        }

        int restored = 0;
        try (var paths = Files.walk(context.get("res", false).toPath())) {
            for (var path : (Iterable<java.nio.file.Path>) paths::iterator) {
                if (!path.toString().endsWith(".xml") || !path.getParent().getFileName().toString().startsWith("layout")) continue;
                if (!Files.readString(path).contains("c4g=\"@null\"")) continue;
                String decoded = "res/" + context.get("res", false).toPath().relativize(path).toString().replace('\\', '/');
                String original = originals.get(decoded);
                if (original == null || original.equals(decoded)) throw new IllegalStateException("Missing original binary layout: " + decoded);
                var originalPath = context.get(original, false).toPath();
                boolean copied = !Files.exists(originalPath);
                var parser = new AXmlResourceParser(table);
                try (var input = Files.newInputStream(context.get(original, true).toPath());
                     var document = context.document(decoded)) {
                    parser.open(input);
                    var nodes = document.getElementsByTagName("*");
                    int element = 0;
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.getEventType() != XmlPullParser.START_TAG) continue;
                        if (element >= nodes.getLength()) throw new IllegalStateException("Layout structure changed: " + decoded);
                        Element node = (Element) nodes.item(element++);
                        if (!node.getTagName().equals(parser.getName())) throw new IllegalStateException("Layout element changed: " + decoded);
                        for (int i = 0; i < parser.getAttributeCount(); i++) {
                            if (parser.getAttributeNameResource(i) != BEHAVIOR || !node.getAttribute("c4g").equals("@null")) continue;
                            String expected = parser.getAttributeValueType(i) == 1 ? CLASSES.get(parser.getAttributeValueData(i)) : null;
                            if (expected == null) throw new IllegalStateException("Unverified missing behavior in " + decoded);
                            node.setAttribute("c4g", expected);
                            restored++;
                        }
                    }
                    if (element != nodes.getLength()) throw new IllegalStateException("Layout structure changed: " + decoded);
                } finally {
                    parser.close();
                    if (copied) Files.deleteIfExists(originalPath);
                }
            }
        }
        if (restored != 56) throw new IllegalStateException("Unexpected missing behavior reference count: " + restored);
        System.out.println("Restored " + restored + " verified behavior references from original compiled layouts");
    }
}
