package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "ExternalUser", indices = {@Index(value = {"User"}), @Index(value = {"Ip", "Port"})})
public class ExternalUserEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "ExternalId", typeAffinity = ColumnInfo.BLOB)
    public byte[] externalId;

    @NonNull
    @ColumnInfo(name = "User", typeAffinity = ColumnInfo.BLOB)
    public byte[] ownerUserId;

    @NonNull
    @ColumnInfo(name = "ExternalUserName")
    public String externalUserName;

    @ColumnInfo(name = "ExternalUserNickname")
    public String externalUserNickname;

    @NonNull
    @ColumnInfo(name = "Ip")
    public String ip;

    @NonNull
    @ColumnInfo(name = "Port")
    public int port;

    @NonNull
    @ColumnInfo(name = "Blocked")
    public boolean blocked = false;
}