package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "ShareResource", indices = {@Index(value = {"OwnerUserId"}), @Index(value = {"Type"}), @Index(value = {"RootTaskId"})})
public class ShareResourceEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "ResourceId", typeAffinity = ColumnInfo.BLOB)
    public byte[] resourceId;

    @NonNull
    @ColumnInfo(name = "OwnerUserId", typeAffinity = ColumnInfo.BLOB)
    public byte[] ownerUserId;

    @NonNull
    @ColumnInfo(name = "Type")
    public String type;

    @NonNull
    @ColumnInfo(name = "Name")
    public String name;

    @NonNull
    @ColumnInfo(name = "RootTaskId", typeAffinity = ColumnInfo.BLOB)
    public byte[] rootTaskId;

    @ColumnInfo(name = "Port")
    public Integer port;

    @NonNull
    @ColumnInfo(name = "PasswordRequired")
    public boolean passwordRequired = true;

    @NonNull
    @ColumnInfo(name = "ConfirmRequired")
    public boolean confirmRequired = true;

    @NonNull
    @ColumnInfo(name = "Active")
    public boolean active = true;
}
