package georgii.sytnik.thothtasks.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class LocaleHelper {

    private static final String PREFS = "thoth_prefs";
    private static final String KEY_LANG = "lang";

    public static void setLocale(Context context, String languageTag) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_LANG, languageTag).apply();
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag));
    }

    public static void applySavedLocale(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String lang = sp.getString(KEY_LANG, "es");
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang));
    }

    public static String getCurrentLocale(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(KEY_LANG, "es");
    }
}