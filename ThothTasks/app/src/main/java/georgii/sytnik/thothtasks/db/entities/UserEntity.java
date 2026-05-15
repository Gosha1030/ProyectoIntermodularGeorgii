package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "User", indices = {@Index(value = {"UserName"}, unique = true), @Index(value = {"TaskRoot"}, unique = true)})
public class UserEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "UserId", typeAffinity = ColumnInfo.BLOB)
    public byte[] userId;

    @NonNull
    @ColumnInfo(name = "TaskRoot", typeAffinity = ColumnInfo.BLOB)
    public byte[] taskRoot;

    @NonNull
    @ColumnInfo(name = "UserName")
    public String userName;

    @NonNull
    @ColumnInfo(name = "Password")
    public String password;

    @NonNull
    @ColumnInfo(name = "Type")
    public String type;

    @NonNull
    @ColumnInfo(name = "Ip")
    public String ip;

    @ColumnInfo(name = "Port")
    public Integer port;

    @NonNull
    @ColumnInfo(name = "PasswordRequired")
    public boolean passwordRequired = true;

    @NonNull
    @ColumnInfo(name = "ConfirmRequired")
    public boolean confirmRequired = true;

    @ColumnInfo(name = "Ajustes")
    public String ajustesJson;
}
