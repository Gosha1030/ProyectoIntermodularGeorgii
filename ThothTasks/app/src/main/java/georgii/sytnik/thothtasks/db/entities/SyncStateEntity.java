package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "SyncState",
        indices = {
                @Index(value = {"PeerKey", "ResourceId"}, unique = true)
        }
)
public class SyncStateEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "SyncId", typeAffinity = ColumnInfo.BLOB)
    public byte[] syncId;

    // peerKey = ip:port for LAN v1
    @NonNull
    @ColumnInfo(name = "PeerKey")
    public String peerKey;

    @NonNull
    @ColumnInfo(name = "ResourceId", typeAffinity = ColumnInfo.BLOB)
    public byte[] resourceId;

    @NonNull
    @ColumnInfo(name = "LastSeenUtcMs")
    public long lastSeenUtcMs;

    @NonNull
    @ColumnInfo(name = "LastSyncUtcMs")
    public long lastSyncUtcMs;

    @NonNull
    @ColumnInfo(name = "LastRemoteVersion")
    public long lastRemoteVersion;

    @NonNull
    @ColumnInfo(name = "LastAppliedVersion")
    public long lastAppliedVersion;

    @NonNull
    @ColumnInfo(name = "HasUpdate")
    public boolean hasUpdate;

    @ColumnInfo(name = "LastError")
    public String lastError;

    @NonNull
    @ColumnInfo(name = "LastNotifiedVersion")
    public long lastNotifiedVersion;
}
