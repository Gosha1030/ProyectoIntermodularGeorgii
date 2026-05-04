package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "Travel",
        indices = {
                @Index(value = {"Start", "Finish"}, unique = true)
        }
)
public class TravelEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "TravelId", typeAffinity = ColumnInfo.BLOB)
    public byte[] travelId;

    @NonNull
    @ColumnInfo(name = "Start", typeAffinity = ColumnInfo.BLOB)
    public byte[] startPlaceId;

    @NonNull
    @ColumnInfo(name = "Finish", typeAffinity = ColumnInfo.BLOB)
    public byte[] finishPlaceId;

    @ColumnInfo(name = "Type")
    public String type;

    @NonNull
    @ColumnInfo(name = "TimeM")
    public int timeM;

    @ColumnInfo(name = "UserTimeM")
    public Integer userTimeM;

    @ColumnInfo(name = "GoogleTimeM")
    public Integer googleTimeM;

    @ColumnInfo(name = "GoogleData")
    public String googleDataJson;
}