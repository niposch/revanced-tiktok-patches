package android.util;
/** JVM test fixture; the device provides the real Android class. */
public final class Log {
    public static String lastMessage;
    public static int i(String tag, String message) { lastMessage = message; return 0; }
}
