package app.revanced.tiktok;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

/** Public AppLog registration identity; independent of any installed original. */
public final class RegistrationIdentity {
    private static final String ORIGINAL = "com.zhiliaoapp.musically";
    private static final String FILTERED = ORIGINAL + ".filtered";
    private static boolean reported;
    private RegistrationIdentity() {}

    public static JSONObject put(JSONObject header, String key, Object value) throws JSONException {
        if ("package".equals(key) && FILTERED.equals(value)) {
            value = ORIGINAL;
            if (!reported) {
                reported = true;
                Log.i("TikTokRegistration", "Alongside registration package corrected");
            }
        }
        return header.put(key, value);
    }
}
