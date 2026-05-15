package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "PendingOutbox", indices = {@Index(value = {"PeerKey"}), @Index(value = {"NextRetryUtcMs"})})
public class PendingOutboxEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "MsgId", typeAffinity = ColumnInfo.BLOB)
    public byte[] msgId;

    @NonNull
    @ColumnInfo(name = "PeerKey")
    public String peerKey;

    @NonNull
    @ColumnInfo(name = "PayloadJson")
    public String payloadJson;

    @NonNull
    @ColumnInfo(name = "Attempts")
    public int attempts;

    @NonNull
    @ColumnInfo(name = "NextRetryUtcMs")
    public long nextRetryUtcMs;

    @NonNull
    @ColumnInfo(name = "CreatedUtcMs")
    public long createdUtcMs;
}