package georgii.sytnik.thothtasks;

import android.app.Application;
import georgii.sytnik.thothtasks.util.LocaleHelper;

public class ThothApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LocaleHelper.applySavedLocale(this);
    }
}