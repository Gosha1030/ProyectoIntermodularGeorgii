package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "TaskChange", indices = {@Index(value = {"Task"}), @Index(value = {"WhenApply"}), @Index(value = {"Task", "CreateAt"})})
public class TaskChangeEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "TaskChangeId", typeAffinity = ColumnInfo.BLOB)
    public byte[] taskChangeId;

    @NonNull
    @ColumnInfo(name = "Task", typeAffinity = ColumnInfo.BLOB)
    public byte[] taskId;

    @ColumnInfo(name = "NewTask", typeAffinity = ColumnInfo.BLOB)
    public byte[] newTaskId;

    @NonNull
    @ColumnInfo(name = "Type")
    public String type;

    @NonNull
    @ColumnInfo(name = "CreateAt")
    public long createAtUtcMs;

    @ColumnInfo(name = "WhenApply")
    public Long whenApplyUtcMs;
}