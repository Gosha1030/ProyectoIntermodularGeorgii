package georgii.sytnik.thothtasks.net;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import georgii.sytnik.thothtasks.R;

public final class Notify {

    private static final String CHANNEL = "connections";

    private Notify() {}

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL,
                    "Connections",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
    }

    public static void showAccessRequest(
            Context ctx,
            String localName,      // ✅ solo nombre (tu opción A)
            String peerIp,
            int peerPort,
            String resourceIdHex,
            String requestMsgIdHex,
            String externalName
    ) {
        ensureChannel(ctx);

        Intent accept = new Intent(ctx, AccessDecisionReceiver.class);
        accept.setAction(Protocol.ACTION_ACCEPT);
        accept.putExtra(Protocol.EXTRA_PEER_IP, peerIp);
        accept.putExtra(Protocol.EXTRA_PEER_PORT, peerPort);
        accept.putExtra(Protocol.EXTRA_RESOURCE_ID_HEX, resourceIdHex);
        accept.putExtra(Protocol.EXTRA_REQUEST_MSGID_HEX, requestMsgIdHex);
        accept.putExtra(Protocol.EXTRA_EXTERNAL_NAME, externalName);

        Intent reject = new Intent(ctx, AccessDecisionReceiver.class);
        reject.setAction(Protocol.ACTION_REJECT);
        reject.putExtras(accept);

        Intent block = new Intent(ctx, AccessDecisionReceiver.class);
        block.setAction(Protocol.ACTION_BLOCK);
        block.putExtras(accept);

        PendingIntent pAccept = PendingIntent.getBroadcast(ctx, 1, accept, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pReject = PendingIntent.getBroadcast(ctx, 2, reject, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pBlock  = PendingIntent.getBroadcast(ctx, 3, block,  PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Solicitud de acceso")
                .setContentText("Quiere acceso a: " + localName)
                .setAutoCancel(true)
                .addAction(0, "Aceptar", pAccept)
                .addAction(0, "Rechazar", pReject)
                .addAction(0, "Bloquear", pBlock);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify((int) (System.currentTimeMillis() & 0x7FFFFFFF), b.build());
    }
}