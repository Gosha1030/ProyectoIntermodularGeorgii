package georgii.sytnik.thothtasks.util;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREFS = "thoth_session";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_ASK_PASSWORD = "ask_password";

    private final SharedPreferences sp;

    public SessionManager(Context context) {
        sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void login(long userId) {
        sp.edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putLong(KEY_USER_ID, userId)
                .apply();
    }

    public void logout() {
        sp.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return sp.getBoolean(KEY_LOGGED_IN, false);
    }

    public long getUserId() {
        return sp.getLong(KEY_USER_ID, -1);
    }

    public boolean isAskPasswordEnabled() {
        return sp.getBoolean(KEY_ASK_PASSWORD, true);
    }

    public void setAskPasswordEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_ASK_PASSWORD, enabled).apply();
    }
}