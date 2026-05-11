package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "RemoteTaskMap",
        indices = { @Index(value = {"SourceId", "RemoteTaskId"}, unique = true) }
)
public class RemoteTaskMapEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "MapId", typeAffinity = ColumnInfo.BLOB)
    public byte[] mapId;

    @NonNull
    @ColumnInfo(name = "SourceId", typeAffinity = ColumnInfo.BLOB)
    public byte[] sourceId;

    @NonNull
    @ColumnInfo(name = "RemoteTaskId", typeAffinity = ColumnInfo.BLOB)
    public byte[] remoteTaskId;

    @NonNull
    @ColumnInfo(name = "LocalTaskId", typeAffinity = ColumnInfo.BLOB)
    public byte[] localTaskId;
}
