package georgii.sytnik.thothtasks.security;

import org.json.JSONObject;

import georgii.sytnik.thothtasks.db.entities.UserEntity;

public final class ActionSettingsReader {
    private ActionSettingsReader() {}

    public static int planDaysAhead(UserEntity u) {
        JSONObject o = SettingsJson.parseOrEmpty(u.ajustesJson);
        int d = SettingsJson.getInt(o, "actionPlanDaysAhead", 60);
        if (d < 1) d = 1;
        if (d > 365) d = 365;
        return d;
    }
}