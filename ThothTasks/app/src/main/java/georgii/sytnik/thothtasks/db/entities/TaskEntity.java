package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "Task",
        indices = {
                @Index(value = {"TaskFather"}),
                @Index(value = {"State", "Muted", "Type"}),
                @Index(value = {"Place"})
        }
)
public class TaskEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "TaskId", typeAffinity = ColumnInfo.BLOB)
    public byte[] taskId;

    @ColumnInfo(name = "TaskFather", typeAffinity = ColumnInfo.BLOB)
    public byte[] taskFather; // nullable

    @NonNull
    @ColumnInfo(name = "TaskName")
    public String taskName;

    /**
     * Unique, Daily, Weekly, Yearly, Periodic, Empty
     */
    @NonNull
    @ColumnInfo(name = "Type")
    public String type;

    @ColumnInfo(name = "PeriodD")
    public Integer periodD;

    /**
     * JSON TEXT (DaysOf)
     */
    @ColumnInfo(name = "DaysOf")
    public String daysOfJson;

    /**
     * JSON TEXT (Periodic rule)
     */
    @ColumnInfo(name = "Periodic")
    public String periodicJson;

    @NonNull
    @ColumnInfo(name = "State")
    public boolean state = true;

    /**
     * StartTime/FinishTime in minutes 0..1439 (INTEGER).
     */
    @ColumnInfo(name = "StartTime")
    public Integer startTimeMin;

    @ColumnInfo(name = "FinishTime")
    public Integer finishTimeMin;

    @ColumnInfo(name = "TimeM")
    public Integer timeM;

    @NonNull
    @ColumnInfo(name = "Uninterrupted")
    public boolean uninterrupted = true;

    @ColumnInfo(name = "Weight")
    public Integer weight;

    /**
     * JSON TEXT (Action)
     */
    @ColumnInfo(name = "Action")
    public String actionJson;

    @NonNull
    @ColumnInfo(name = "Muted")
    public boolean muted = false;

    @ColumnInfo(name = "Place", typeAffinity = ColumnInfo.BLOB)
    public byte[] placeId;
}
