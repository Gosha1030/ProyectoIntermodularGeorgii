package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "AccessGrant", indices = {@Index(value = {"ExternalUserId"}), @Index(value = {"ResourceId"}), @Index(value = {"ExternalUserId", "ResourceId"}, unique = true)})
public class AccessGrantEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "GrantId", typeAffinity = ColumnInfo.BLOB)
    public byte[] grantId;

    @NonNull
    @ColumnInfo(name = "ExternalUserId", typeAffinity = ColumnInfo.BLOB)
    public byte[] externalUserId;

    @NonNull
    @ColumnInfo(name = "ResourceId", typeAffinity = ColumnInfo.BLOB)
    public byte[] resourceId;

    @NonNull
    @ColumnInfo(name = "Granted")
    public boolean granted = false;

    @NonNull
    @ColumnInfo(name = "GrantedAtUtcMs")
    public long grantedAtUtcMs;

    @ColumnInfo(name = "RevokedAtUtcMs")
    public Long revokedAtUtcMs;
}