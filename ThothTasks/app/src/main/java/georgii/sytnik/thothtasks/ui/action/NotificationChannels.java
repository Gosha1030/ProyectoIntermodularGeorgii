package georgii.sytnik.thothtasks.ui.action;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import georgii.sytnik.thothtasks.R;

public final class NotificationChannels {

    public static final String CHANNEL_ACTIONS = "actions";

    public static final String CHANNEL_ALARMS = "actions_alarm";

    private NotificationChannels() {
    }

    public static void ensureCreated(Context ctx) {
        if (ctx == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel actions = new NotificationChannel(
                CHANNEL_ACTIONS,
                ctx.getString(R.string.channel_actions_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        actions.setDescription(ctx.getString(R.string.channel_actions_desc));
        nm.createNotificationChannel(actions);

        NotificationChannel alarms = new NotificationChannel(
                CHANNEL_ALARMS,
                ctx.getString(R.string.channel_alarms_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        alarms.setDescription(ctx.getString(R.string.channel_alarms_desc));
        alarms.enableVibration(true);
        nm.createNotificationChannel(alarms);
    }
}
