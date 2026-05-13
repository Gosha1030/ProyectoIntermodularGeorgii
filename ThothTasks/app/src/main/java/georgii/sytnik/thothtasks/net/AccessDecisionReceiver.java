package georgii.sytnik.thothtasks.net;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.util.UUID;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.AccessGrantEntity;
import georgii.sytnik.thothtasks.db.entities.AccessRequestEntity;
import georgii.sytnik.thothtasks.db.entities.ExternalUserEntity;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;
import georgii.sytnik.thothtasks.util.UuidBytes;

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

                // Resolver ExternalUser + ResourceId + ackOfHex desde DB (para soportar reintentos)
                byte[] resourceId = MessageCodec.hexToBytes(resourceIdHex);
                ExternalUserEntity eu = UdpOwnerService.findOrCreateExternalUser(db, ownerUserId, ip, port, externalName);

                String ackOfHex = reqMsgIdHex;
                AccessRequestEntity ar = db.accessRequestDao().find(eu.externalId, resourceId);
                if (ar != null && ar.requestMsgIdHex != null && !ar.requestMsgIdHex.isEmpty()) {
                    ackOfHex = ar.requestMsgIdHex;
                }

                long now = System.currentTimeMillis();


                if (Protocol.ACTION_BLOCK.equals(action)) {
                    // ✅ usa la firma nueva
                    db.externalUserDao().setBlocked(eu.externalId, true);
                    db.accessRequestDao().setDecision(eu.externalId, resourceId, AccessRequestEntity.STATE_BLOCKED, now);
                    sendAccessResult(context, ip, port, resourceIdHex, false, "BLOCKED", ackOfHex);
                    return;
                }

                if (Protocol.ACTION_REJECT.equals(action)) {
                    db.accessRequestDao().setDecision(eu.externalId, resourceId, AccessRequestEntity.STATE_REJECTED, now);
                    sendAccessResult(context, ip, port, resourceIdHex, false, "REJECTED", ackOfHex);
                    return;
                }

                if (Protocol.ACTION_ACCEPT.equals(action)) {
                    // ✅ usa la firma nueva
                    // create / update grant
                    AccessGrantEntity g = db.accessGrantDao().find(eu.externalId, resourceId);

                    if (g == null) {
                        g = new AccessGrantEntity();
                        g.grantId = UuidBytes.uuidToBytes(UuidV7.newUuid());
                        g.externalUserId = eu.externalId;
                        g.resourceId = resourceId;
                    }

                    g.granted = true;
                    g.grantedAtUtcMs = System.currentTimeMillis();
                    g.revokedAtUtcMs = null;
                    db.accessGrantDao().upsert(g);

                    db.accessRequestDao().setDecision(eu.externalId, resourceId, AccessRequestEntity.STATE_ACCEPTED, now);
                    sendAccessResult(context, ip, port, resourceIdHex, true, "OK", ackOfHex);
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

}