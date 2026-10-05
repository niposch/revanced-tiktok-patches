import app.revanced.tiktok.RegistrationIdentity;
import org.json.JSONObject;
import org.json.JSONException;

public final class RegistrationIdentityTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        JSONObject header = new JSONObject();
        check(RegistrationIdentity.put(header, "package", "com.zhiliaoapp.musically.filtered") == header, "JSON chaining identity lost");
        check(header.get("package").equals("com.zhiliaoapp.musically"), "Registration identity incorrect");
        RegistrationIdentity.put(header, "real_package_name", "com.zhiliaoapp.musically.filtered");
        check(header.get("real_package_name").equals("com.zhiliaoapp.musically.filtered"), "Real package metadata changed");
        RegistrationIdentity.put(header, "sig_hash", "actual-signing-certificate");
        check(header.get("sig_hash").equals("actual-signing-certificate"), "Certificate metadata changed");
        RegistrationIdentity.put(header, "package", "another.app");
        check(header.get("package").equals("another.app"), "Unrelated package changed");
        RegistrationIdentity.put(header, "package", "com.zhiliaoapp.musically");
        check(header.get("package").equals("com.zhiliaoapp.musically"), "Original identity changed");
        RegistrationIdentity.put(header, "package", null);
        try { header.get("package"); throw new AssertionError("Null-removal semantics changed"); } catch (JSONException expected) {}
        try { RegistrationIdentity.put(header, null, "value"); throw new AssertionError("JSON error swallowed"); } catch (JSONException expected) {}
        System.out.println("Registration identity scope, metadata and JSON behavior checks passed");
    }
}
