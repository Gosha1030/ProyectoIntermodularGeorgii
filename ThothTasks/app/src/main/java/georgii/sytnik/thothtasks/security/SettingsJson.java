package georgii.sytnik.thothtasks.security;

import org.json.JSONObject;

public final class SettingsJson {
    private SettingsJson() {}

    public static JSONObject parseOrEmpty(String json) {
        try {
            if (json == null || json.trim().isEmpty()) return new JSONObject();
            return new JSONObject(json);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static boolean getBool(JSONObject o, String k, boolean def) {
        return o.has(k) ? o.optBoolean(k, def) : def;
    }

    public static int getInt(JSONObject o, String k, int def) {
        return o.has(k) ? o.optInt(k, def) : def;
    }

    public static String getString(JSONObject o, String k, String def) {
        if (o == null) return def;
        String v = o.optString(k, null);
        return (v == null || v.isEmpty()) ? def : v;
    }

    public static void putBool(JSONObject o, String k, boolean v) {
        try { o.put(k, v); } catch (Exception ignored) {}
    }

    public static void putInt(JSONObject o, String k, int v) {
        try { o.put(k, v); } catch (Exception ignored) {}
    }

    public static void putString(JSONObject o, String k, String v) {
        try { o.put(k, v); } catch (Exception ignored) {}
    }
}