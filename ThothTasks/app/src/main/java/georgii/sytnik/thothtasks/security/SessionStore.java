package georgii.sytnik.thothtasks.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

public final class SessionStore {

    private static final String PREFS = "thoth_session";
    private static final String KEY_LAST_USER_ID = "last_user_id_b64";

    private SessionStore() {}

    public static void saveLastUserId(Context ctx, byte[] userId) {
        String b64 = Base64.encodeToString(userId, Base64.NO_WRAP);
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_LAST_USER_ID, b64).apply();
    }

    public static byte[] loadLastUserId(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String b64 = sp.getString(KEY_LAST_USER_ID, null);
        if (b64 == null) return null;
        return Base64.decode(b64, Base64.NO_WRAP);
    }

    public static void clear(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().clear().apply();
    }
}
