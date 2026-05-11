package georgii.sytnik.thothtasks.ui.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;
import java.util.UUID;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.ExternalSourceEntity;
import georgii.sytnik.thothtasks.db.entities.SyncStateEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.net.MessageCodec;
import georgii.sytnik.thothtasks.net.NotifySync;
import georgii.sytnik.thothtasks.net.VersionClient;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;

public class UpdateCheckWorker extends Worker {

    public UpdateCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppDatabase db = AppDatabase.get(getApplicationContext());

        try {
            byte[] ownerUserId = SessionStore.loadLastUserId(getApplicationContext());
            if (ownerUserId == null) return Result.success();

            UserEntity owner = db.userDao().findById(ownerUserId);

            // ⚠️ Ajusta este campo al nombre real en tu UserEntity:
            // Si no existe owner.userName, cambia por owner.userName / owner.userNameText / owner.userNameStr / etc.
            String externalName = (owner != null && owner.userName != null) ? owner.userName : "external";

            List<ExternalSourceEntity> sources = db.externalSourceDao().listAll();
            long now = System.currentTimeMillis();

            for (ExternalSourceEntity src : sources) {
                if (src.blocked) continue;

                String peerKey = src.ip + ":" + src.port;

                SyncStateEntity st = db.syncStateDao().find(peerKey, src.resourceId);

                long applied = (st != null) ? st.lastAppliedVersion : 0;

                long remoteVersion;
                try {
                    remoteVersion = VersionClient.requestRemoteVersion(
                            src.ip,
                            src.port,
                            MessageCodec.hex(src.resourceId),
                            externalName,
                            2000
                    );
                } catch (Exception e) {
                    // offline / unreachable -> no spam
                    continue;
                }

                boolean hasUpdate = remoteVersion > applied;

                if (st == null) {
                    st = new SyncStateEntity();
                    st.syncId = uuidToBytes(UuidV7.newUuid());
                    st.peerKey = peerKey;
                    st.resourceId = src.resourceId;
                    st.lastAppliedVersion = applied;
                    st.lastNotifiedVersion = 0;
                }

                st.lastSeenUtcMs = now;
                st.lastRemoteVersion = remoteVersion;
                st.hasUpdate = hasUpdate;

                // ✅ Notificar SOLO una vez por versión nueva
                if (hasUpdate && remoteVersion > st.lastNotifiedVersion) {
                    int notifId = stableNotifId(src.sourceId);
                    NotifySync.showUpdateAvailable(getApplicationContext(), notifId, src.displayName);
                    st.lastNotifiedVersion = remoteVersion;
                }

                db.syncStateDao().upsert(st);
            }

            return Result.success();

        } catch (Exception e) {
            return Result.retry();
        }
    }

    private int stableNotifId(byte[] sourceId) {
        int h = 0;
        if (sourceId != null) for (byte b : sourceId) h = h * 31 + (b & 0xFF);
        return h & 0x7FFFFFFF;
    }

    private static byte[] uuidToBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        return new byte[] {
                (byte)(msb >>> 56), (byte)(msb >>> 48), (byte)(msb >>> 40), (byte)(msb >>> 32),
                (byte)(msb >>> 24), (byte)(msb >>> 16), (byte)(msb >>>  8), (byte)(msb),
                (byte)(lsb >>> 56), (byte)(lsb >>> 48), (byte)(lsb >>> 40), (byte)(lsb >>> 32),
                (byte)(lsb >>> 24), (byte)(lsb >>> 16), (byte)(lsb >>>  8), (byte)(lsb)
        };
    }
}