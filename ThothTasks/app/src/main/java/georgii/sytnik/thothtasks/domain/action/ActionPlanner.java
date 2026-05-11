package georgii.sytnik.thothtasks.domain.action;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.ui.action.ActionReceiver;

public final class ActionPlanner {

    private ActionPlanner() {}

    public static void scheduleNextDays(Context ctx, AppDatabase db, int daysAhead) {
        SharedPreferences sp = ctx.getSharedPreferences("actions", Context.MODE_PRIVATE);
        long last = sp.getLong("lastPlanUtc", 0);
        long now = System.currentTimeMillis();
        if (now - last < 1500) return; // debounce 1.5s
        sp.edit().putLong("lastPlanUtc", now).apply();

        Calendar day = Calendar.getInstance();
        zeroTime(day);

        for (int i = 0; i < daysAhead; i++) {
            Calendar d = (Calendar) day.clone();
            d.add(Calendar.DATE, i);
            DayActionPlan.buildAndScheduleDay(ctx, db, d);
        }
    }



    static void scheduleAt(Context ctx, long whenUtcMs, String kind, String text, int requestCode) {
        if (whenUtcMs < System.currentTimeMillis() + 1000) return; // ignore past/too-soon

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

        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenUtcMs, pi);
    }

    static int stableCode(String s) {
        int h = 0;
        for (int i = 0; i < s.length(); i++) h = h * 31 + s.charAt(i);
        return h & 0x7fffffff;
    }

    private static void zeroTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}