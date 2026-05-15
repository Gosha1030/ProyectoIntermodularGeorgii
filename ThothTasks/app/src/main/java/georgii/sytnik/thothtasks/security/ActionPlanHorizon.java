package georgii.sytnik.thothtasks.security;

import android.content.Context;

import org.json.JSONObject;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.UserEntity;

public final class ActionPlanHorizon {

    private ActionPlanHorizon() {
    }

    public static int getDaysAhead(Context ctx, AppDatabase db) {
        try {
            byte[] userId = SessionStore.loadLastUserId(ctx);
            if (userId == null) return 60;

            UserEntity u = db.userDao().findById(userId);
            if (u == null) return 60;

            JSONObject o = SettingsJson.parseOrEmpty(u.ajustesJson);
            int d = SettingsJson.getInt(o, "actionPlanDaysAhead", 60);
            if (d < 1) d = 1;
            if (d > 365) d = 365;
            return d;
        } catch (Exception e) {
            return 60;
        }
    }
}