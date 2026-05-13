package georgii.sytnik.thothtasks.ui.action;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import georgii.sytnik.thothtasks.R;

/** Executes scheduled actions (notifications + DND reference counting). */
public class ActionReceiver extends BroadcastReceiver {

    public static final String EXTRA_KIND = "kind";   // NOTIFY | ALARM | DND_ON | DND_OFF
    public static final String EXTRA_TEXT = "text";

    public static final String KIND_NOTIFY  = "NOTIFY";
    public static final String KIND_ALARM   = "ALARM";
    public static final String KIND_DND_ON  = "DND_ON";
    public static final String KIND_DND_OFF = "DND_OFF";

    private static final String PREFS_DND = "dnd";
    private static final String KEY_CNT = "cnt";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Ensure channels exist (Android 8+)
        NotificationChannels.ensureCreated(context);

        String kind = intent.getStringExtra(EXTRA_KIND);
        String text = intent.getStringExtra(EXTRA_TEXT);
        if (text == null) text = "";

        if (KIND_DND_ON.equals(kind) || KIND_DND_OFF.equals(kind)) {
            handleDnd(context, kind);
        }

        boolean isAlarm = KIND_ALARM.equals(kind);
        String channelId = isAlarm ? NotificationChannels.CHANNEL_ALARMS : NotificationChannels.CHANNEL_ACTIONS;

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(titleFor(context, kind))
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(isAlarm ? NotificationCompat.PRIORITY_MAX : NotificationCompat.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            int id = (int) (System.currentTimeMillis() & 0x7fffffff);
            nm.notify(id, b.build());
        }
    }

    private String titleFor(Context ctx, String kind) {
        if (KIND_ALARM.equals(kind)) return ctx.getString(R.string.action_title_alarm);
        if (KIND_NOTIFY.equals(kind)) return ctx.getString(R.string.action_title_notify);
        if (KIND_DND_ON.equals(kind)) return ctx.getString(R.string.action_title_dnd_on);
        if (KIND_DND_OFF.equals(kind)) return ctx.getString(R.string.action_title_dnd_off);
        return ctx.getString(R.string.action_title_action);
    }

    /** DND reference counter (nested ON/OFF) using NotificationManager interruption filter. */
    private void handleDnd(Context ctx, String kind) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (!nm.isNotificationPolicyAccessGranted()) return;

        int cnt = ctx.getSharedPreferences(PREFS_DND, Context.MODE_PRIVATE).getInt(KEY_CNT, 0);
        if (KIND_DND_ON.equals(kind)) cnt++;
        else if (KIND_DND_OFF.equals(kind)) cnt = Math.max(0, cnt - 1);

        ctx.getSharedPreferences(PREFS_DND, Context.MODE_PRIVATE).edit().putInt(KEY_CNT, cnt).apply();

        if (cnt > 0) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        else nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
    }
}
