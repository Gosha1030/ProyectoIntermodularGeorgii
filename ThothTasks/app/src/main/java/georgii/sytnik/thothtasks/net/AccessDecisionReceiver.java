package georgii.sytnik.thothtasks.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.util.UUID;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.AccessGrantEntity;
import georgii.sytnik.thothtasks.db.entities.ExternalUserEntity;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;

public class AccessDecisionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String ip = intent.getStringExtra(Protocol.EXTRA_PEER_IP);
        int port = intent.getIntExtra(Protocol.EXTRA_PEER_PORT, 0);
        String resourceIdHex = intent.getStringExtra(Protocol.EXTRA_RESOURCE_ID_HEX);
        String reqMsgIdHex = intent.getStringExtra(Protocol.EXTRA_REQUEST_MSGID_HEX);
        String externalName = intent.getStringExtra(Protocol.EXTRA_EXTERNAL_NAME);

        AppDatabase db = AppDatabase.get(context);

        new Thread(() -> {
            try {
                // ✅ OwnerUserId requerido por la nueva firma
                byte[] ownerUserId = SessionStore.loadLastUserId(context);
                if (ownerUserId == null) {
                    // No hay sesión => no se puede decidir correctamente
                    sendAccessResult(context, ip, port, resourceIdHex, false, "NO_OWNER_SESSION", reqMsgIdHex);
                    return;
                }

                if (Protocol.ACTION_BLOCK.equals(action)) {
                    // ✅ usa la firma nueva
                    ExternalUserEntity eu = UdpOwnerService.findOrCreateExternalUser(db, ownerUserId, ip, port, externalName);
                    db.externalUserDao().setBlocked(eu.externalId, true);
                    sendAccessResult(context, ip, port, resourceIdHex, false, "BLOCKED", reqMsgIdHex);
                    return;
                }

                if (Protocol.ACTION_REJECT.equals(action)) {
                    sendAccessResult(context, ip, port, resourceIdHex, false, "REJECTED", reqMsgIdHex);
                    return;
                }

                if (Protocol.ACTION_ACCEPT.equals(action)) {
                    // ✅ usa la firma nueva
                    ExternalUserEntity eu = UdpOwnerService.findOrCreateExternalUser(db, ownerUserId, ip, port, externalName);

                    // create / update grant
                    byte[] resourceId = MessageCodec.hexToBytes(resourceIdHex);
                    AccessGrantEntity g = db.accessGrantDao().find(eu.externalId, resourceId);

                    if (g == null) {
                        g = new AccessGrantEntity();
                        g.grantId = uuidToBytes(UuidV7.newUuid());
                        g.externalUserId = eu.externalId;
                        g.resourceId = resourceId;
                    }

                    g.granted = true;
                    g.grantedAtUtcMs = System.currentTimeMillis();
                    g.revokedAtUtcMs = null;
                    db.accessGrantDao().upsert(g);

                    sendAccessResult(context, ip, port, resourceIdHex, true, "OK", reqMsgIdHex);
                }

            } catch (Exception ignored) {
                // opcional: log
            }
        }).start();
    }

    private static void sendAccessResult(Context ctx, String ip, int port,
                                         String resourceIdHex,
                                         boolean granted,
                                         String reason,
                                         String ackOfHex) throws Exception {

        JSONObject body = new JSONObject();
        body.put("resourceId", resourceIdHex);
        body.put("granted", granted);
        body.put("reason", reason);

        JSONObject env = MessageCodec.envelope(
                Protocol.ACCESS_RESULT,
                "owner", // v1 placeholder
                1,
                ackOfHex,
                body
        );

        UdpOwnerService.sendRaw(ctx, ip, port, env);
    }

    private static byte[] uuidToBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        return new byte[]{
                (byte) (msb >>> 56), (byte) (msb >>> 48), (byte) (msb >>> 40), (byte) (msb >>> 32),
                (byte) (msb >>> 24), (byte) (msb >>> 16), (byte) (msb >>> 8), (byte) (msb),
                (byte) (lsb >>> 56), (byte) (lsb >>> 48), (byte) (lsb >>> 40), (byte) (lsb >>> 32),
                (byte) (lsb >>> 24), (byte) (lsb >>> 16), (byte) (lsb >>> 8), (byte) (lsb)
        };
    }
}