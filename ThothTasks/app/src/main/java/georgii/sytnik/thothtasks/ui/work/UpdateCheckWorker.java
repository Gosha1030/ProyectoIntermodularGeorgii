package georgii.sytnik.thothtasks.ui.work;

import static georgii.sytnik.thothtasks.util.HexBytes.hex;
import static georgii.sytnik.thothtasks.util.UuidBytes.uuidToBytes;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.ExternalSourceEntity;
import georgii.sytnik.thothtasks.db.entities.SyncStateEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
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
                    remoteVersion = VersionClient.requestRemoteVersion(src.ip, src.port, hex(src.resourceId), externalName, 2000);
                } catch (Exception e) {
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
}