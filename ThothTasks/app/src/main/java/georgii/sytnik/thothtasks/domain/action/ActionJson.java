package georgii.sytnik.thothtasks.domain.action;

import org.json.JSONObject;

public final class ActionJson {
    private ActionJson() {}

    public static JSONObject parseOrEmpty(String json) {
        try {
            if (json == null || json.trim().isEmpty()) return new JSONObject();
            return new JSONObject(json);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static boolean get(String json, String key) {
        return parseOrEmpty(json).optBoolean(key, false);
    }

    public static String set(String json, String key, boolean value) {
        JSONObject o = parseOrEmpty(json);
        try { o.put(key, value); } catch (Exception ignored) {}
        return o.toString();
    }
}
