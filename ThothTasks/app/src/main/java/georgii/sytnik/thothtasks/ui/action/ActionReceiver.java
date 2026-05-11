package georgii.sytnik.thothtasks.ui.action;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

public class ActionReceiver extends BroadcastReceiver {

    public static final String EXTRA_KIND = "kind";   // NOTIFY | ALARM | DND_ON | DND_OFF
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_DND_TOKEN = "dndToken"; // optional stable key

    @Override
    public void onReceive(Context context, Intent intent) {
        String kind = intent.getStringExtra(EXTRA_KIND);
        String text = intent.getStringExtra(EXTRA_TEXT);
        if (text == null) text = "";

        if ("DND_ON".equals(kind) || "DND_OFF".equals(kind)) {
            handleDnd(context, kind);
            // opcional: también notificar
        }

        // Always show notification for visibility (v1)
        int notifId = (int) (System.currentTimeMillis() & 0x7fffffff);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, "actions")
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(kind != null ? kind : "ACTION")
                .setContentText(text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(notifId, b.build());
    }

    private void handleDnd(Context ctx, String kind) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // requires user-granted DND access
        if (!nm.isNotificationPolicyAccessGranted()) {
            // no permission: do nothing (notification already tells user)
            return;
        }

        // reference counter (so overlaps don’t break)
        android.content.SharedPreferences sp = ctx.getSharedPreferences("dnd", Context.MODE_PRIVATE);
        int cnt = sp.getInt("cnt", 0);

        if ("DND_ON".equals(kind)) cnt++;
        else if ("DND_OFF".equals(kind)) cnt = Math.max(0, cnt - 1);

        sp.edit().putInt("cnt", cnt).apply();

        if (cnt > 0) nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        else nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
    }
}