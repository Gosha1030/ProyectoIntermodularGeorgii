package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "ExternalSource", indices = {@Index(value = {"Ip", "Port"}), @Index(value = {"ResourceId"})})
public class ExternalSourceEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "SourceId", typeAffinity = ColumnInfo.BLOB)
    public byte[] sourceId;

    @NonNull
    @ColumnInfo(name = "DisplayName")
    public String displayName;

    @NonNull
    @ColumnInfo(name = "Ip")
    public String ip;

    @NonNull
    @ColumnInfo(name = "Port")
    public int port;

    @NonNull
    @ColumnInfo(name = "ResourceId", typeAffinity = ColumnInfo.BLOB)
    public byte[] resourceId;

    @ColumnInfo(name = "RemotePubKeyB64")
    public String remotePubKeyB64;

    @NonNull
    @ColumnInfo(name = "Blocked")
    public boolean blocked = false;

    @NonNull
    @ColumnInfo(name = "IncludedInSchedule")
    public boolean includedInSchedule = true;

    @ColumnInfo(name = "ImportedRootTaskId", typeAffinity = ColumnInfo.BLOB)
    public byte[] importedRootTaskId;
}
