package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "Place",
        indices = {
                @Index(value = {"PlaceName"}, unique = true)
        }
)
public class PlaceEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "PlaceId", typeAffinity = ColumnInfo.BLOB)
    public byte[] placeId;

    @NonNull
    @ColumnInfo(name = "PlaceName")
    public String placeName;

    /**
     * JSON TEXT (GoogleMapsData)
     */
    @ColumnInfo(name = "GoogleMapsData")
    public String googleMapsDataJson;
}