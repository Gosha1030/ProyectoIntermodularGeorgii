package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "ImportedSourceState",
        indices = { @Index(value = {"SourceId"}, unique = true) }
)
public class ImportedSourceStateEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "SourceId", typeAffinity = ColumnInfo.BLOB)
    public byte[] sourceId;

    @NonNull
    @ColumnInfo(name = "LocalRootTaskId", typeAffinity = ColumnInfo.BLOB)
    public byte[] localRootTaskId;

    @NonNull
    @ColumnInfo(name = "LastRemoteVersion")
    public long lastRemoteVersion;
}