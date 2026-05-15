package georgii.sytnik.thothtasks.domain.action;

import static georgii.sytnik.thothtasks.util.TimeText.zeroTime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.ui.action.ActionReceiver;

/**
 * Plans actions for the next N days using AlarmManager.
 * Includes debounce to avoid expensive repeated planning when user toggles multiple switches.
 */
public final class ActionPlanner {

    private static final String PREFS = "actions";
    private static final String KEY_LAST_PLAN_UTC = "lastPlanUtc";

    private ActionPlanner() {
    }

    public static void scheduleNextDays(Context ctx, AppDatabase db, int daysAhead) {
        long now = System.currentTimeMillis();
        long last = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_PLAN_UTC, 0);

        if (now - last < 1500) return; // debounce 1.5s
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_LAST_PLAN_UTC, now).apply();

        Calendar day = Calendar.getInstance();
        zeroTime(day);

        for (int i = 0; i < daysAhead; i++) {
            Calendar d = (Calendar) day.clone();
            d.add(Calendar.DATE, i);
            DayActionPlan.buildAndScheduleDay(ctx, db, d);
        }
    }

    static void scheduleAt(Context ctx, long whenUtcMs, String kind, String text, int requestCode) {
        if (whenUtcMs < System.currentTimeMillis() + 1000) return;

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(ctx, ActionReceiver.class);
        i.putExtra(ActionReceiver.EXTRA_KIND, kind);
        i.putExtra(ActionReceiver.EXTRA_TEXT, text);

        PendingIntent pi = PendingIntent.getBroadcast(
                ctx,
                requestCode,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        am.cancel(pi);
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenUtcMs, pi);
        } catch (SecurityException e) {
        }
    }

    static int stableCode(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) h = h * 31 + s.charAt(i);
        return h & 0x7fffffff;
    }

}