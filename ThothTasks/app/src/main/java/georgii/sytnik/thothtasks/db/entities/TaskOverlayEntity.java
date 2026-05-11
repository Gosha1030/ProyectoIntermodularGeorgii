package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "TaskOverlay",
        indices = {
                @Index(value = {"SourceId", "TaskId"}, unique = true)
        }
)
public class TaskOverlayEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "OverlayId", typeAffinity = ColumnInfo.BLOB)
    public byte[] overlayId;

    @NonNull
    @ColumnInfo(name = "SourceId", typeAffinity = ColumnInfo.BLOB)
    public byte[] sourceId;

    @NonNull
    @ColumnInfo(name = "TaskId", typeAffinity = ColumnInfo.BLOB)
    public byte[] taskId;

    @ColumnInfo(name = "MutedLocal")
    public Boolean mutedLocal;

    @ColumnInfo(name = "ActionLocalJson")
    public String actionLocalJson;

    @NonNull
    @ColumnInfo(name = "UpdatedAtUtcMs")
    public long updatedAtUtcMs;

    @ColumnInfo(name = "PlaceLocalId", typeAffinity = ColumnInfo.BLOB)
    public byte[] placeLocalId;
}