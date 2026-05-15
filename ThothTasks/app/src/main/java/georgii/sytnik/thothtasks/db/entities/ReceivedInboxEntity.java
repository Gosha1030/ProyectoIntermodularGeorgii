package georgii.sytnik.thothtasks.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "ReceivedInbox", indices = {@Index(value = {"ReceivedAtUtcMs"})})
public class ReceivedInboxEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "MsgId", typeAffinity = ColumnInfo.BLOB)
    public byte[] msgId;

    @NonNull
    @ColumnInfo(name = "ReceivedAtUtcMs")
    public long receivedAtUtcMs;
}
