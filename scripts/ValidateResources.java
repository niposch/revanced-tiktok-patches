import brut.androlib.Config;
import brut.androlib.apk.ApkInfo;
import brut.androlib.res.ResourcesDecoder;
import brut.androlib.res.data.value.ResFileValue;
import brut.androlib.res.decoder.AXmlResourceParser;
import brut.directory.ExtFile;
import java.util.*;
import java.util.zip.ZipFile;
import org.xmlpull.v1.XmlPullParser;

/** Compare every compiled layout behavior with the supplied, unmodified APK. */
public final class ValidateResources {
    private static final Map<Integer, String> KNOWN = Map.of(
        0x7f111ebf, "com.google.android.material.bottomsheet.BottomSheetBehavior",
        0x7f1119be, "com.google.android.material.appbar.AppBarLayout$ScrollingViewBehavior"
    );
    private static Map<String, String> read(String apk) throws Exception {
        var decoder = new ResourcesDecoder(Config.getDefaultConfig(), new ApkInfo(new ExtFile(apk)));
        decoder.loadMainPkg();
        var table = decoder.getResTable();
        var behaviors = new TreeMap<String, String>();
        try (var zip = new ZipFile(apk)) {
            for (var pkg : table.listMainPackages()) for (var resource : pkg.listFiles()) {
                if (!resource.getResSpec().getType().getName().equals("layout")) continue;
                String path = "res/" + ((ResFileValue) resource.getValue()).getStrippedPath();
                var parser = new AXmlResourceParser(table);
                try (var input = zip.getInputStream(zip.getEntry(path))) {
                    parser.open(input);
                    int element = 0;
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.getEventType() != XmlPullParser.START_TAG) continue;
                        element++;
                        for (int i = 0; i < parser.getAttributeCount(); i++) {
                            if (parser.getAttributeNameResource(i) != 0x7f060eed) continue;
                            String value = parser.getAttributeValueType(i) == 1 ? KNOWN.get(parser.getAttributeValueData(i)) : parser.getAttributeValue(i);
                            if (value == null || value.equals("@null")) throw new IllegalStateException("Unresolved layout behavior in " + resource.getFilePath());
                            behaviors.put(resource.getFilePath() + ":" + element + ":" + parser.getName(), value);
                        }
                    }
                } finally { parser.close(); }
            }
        }
        return behaviors;
    }
    public static void main(String[] args) throws Exception {
        var original = read(args[0]);
        var patched = read(args[1]);
        if (original.isEmpty() || !original.equals(patched)) {
            var mismatch = new TreeSet<String>(original.keySet());
            mismatch.addAll(patched.keySet());
            mismatch.removeIf(key -> Objects.equals(original.get(key), patched.get(key)));
            throw new AssertionError("Compiled layout behaviors differ: " + mismatch);
        }
        System.out.println("All " + original.size() + " compiled layout behaviors match the original APK");
    }
}
