package org.json;
import java.util.HashMap;
/** JVM fixture; never included in the runtime extension. */
public class JSONObject {
    private final HashMap<String, Object> values = new HashMap<>();
    public JSONObject put(String key, Object value) throws JSONException {
        if (key == null) throw new JSONException("Null key");
        if (value == null) values.remove(key); else values.put(key, value);
        return this;
    }
    public Object get(String key) throws JSONException {
        if (!values.containsKey(key)) throw new JSONException("Missing key");
        return values.get(key);
    }
}
