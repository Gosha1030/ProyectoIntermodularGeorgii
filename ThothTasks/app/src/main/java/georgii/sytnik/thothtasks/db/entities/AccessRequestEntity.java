package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "AccessRequest",
        indices = {
                @Index(value = {"ExternalUserId", "ResourceId"}, unique = true),
                @Index(value = {"State"}),
                @Index(value = {"CreatedAtUtcMs"})
        }
)
public class AccessRequestEntity {

    public static final String STATE_PENDING  = "PENDING";
    public static final String STATE_ACCEPTED = "ACCEPTED";
    public static final String STATE_REJECTED = "REJECTED";
    public static final String STATE_BLOCKED  = "BLOCKED";

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "RequestId", typeAffinity = ColumnInfo.BLOB)
    public byte[] requestId;

    @NonNull
    @ColumnInfo(name = "ExternalUserId", typeAffinity = ColumnInfo.BLOB)
    public byte[] externalUserId;

    @NonNull
    @ColumnInfo(name = "ResourceId", typeAffinity = ColumnInfo.BLOB)
    public byte[] resourceId;

    @NonNull
    @ColumnInfo(name = "State")
    public String state;

    @ColumnInfo(name = "CreatedAtUtcMs")
    public long createdAtUtcMs;

    @ColumnInfo(name = "LastNotifiedAtUtcMs")
    public Long lastNotifiedAtUtcMs;

    @ColumnInfo(name = "DecidedAtUtcMs")
    public Long decidedAtUtcMs;

    @ColumnInfo(name = "PeerIp")
    public String peerIp;

    @ColumnInfo(name = "PeerPort")
    public Integer peerPort;

    @ColumnInfo(name = "RequestMsgIdHex")
    public String requestMsgIdHex;

    @ColumnInfo(name = "ExternalName")
    public String externalName;
}
