package georgii.sytnik.thothtasks.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import georgii.sytnik.thothtasks.db.dao.AccessGrantDao;
import georgii.sytnik.thothtasks.db.dao.AccessRequestDao;
import georgii.sytnik.thothtasks.db.dao.ExternalSourceDao;
import georgii.sytnik.thothtasks.db.dao.ExternalUserDao;
import georgii.sytnik.thothtasks.db.dao.InboxDao;
import georgii.sytnik.thothtasks.db.dao.OutboxDao;
import georgii.sytnik.thothtasks.db.dao.PlaceDao;
import georgii.sytnik.thothtasks.db.dao.ShareResourceDao;
import georgii.sytnik.thothtasks.db.dao.SyncStateDao;
import georgii.sytnik.thothtasks.db.dao.TaskChangeDao;
import georgii.sytnik.thothtasks.db.dao.TaskDao;
import georgii.sytnik.thothtasks.db.dao.TaskOverlayDao;
import georgii.sytnik.thothtasks.db.dao.TravelDao;
import georgii.sytnik.thothtasks.db.dao.UserDao;
import georgii.sytnik.thothtasks.db.entities.AccessGrantEntity;
import georgii.sytnik.thothtasks.db.entities.AccessRequestEntity;
import georgii.sytnik.thothtasks.db.entities.ExternalSourceEntity;
import georgii.sytnik.thothtasks.db.entities.ExternalUserEntity;
import georgii.sytnik.thothtasks.db.entities.PendingOutboxEntity;
import georgii.sytnik.thothtasks.db.entities.PlaceEntity;
import georgii.sytnik.thothtasks.db.entities.ReceivedInboxEntity;
import georgii.sytnik.thothtasks.db.entities.ShareResourceEntity;
import georgii.sytnik.thothtasks.db.entities.SyncStateEntity;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.TaskOverlayEntity;
import georgii.sytnik.thothtasks.db.entities.TravelEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;

@Database(entities = {UserEntity.class, TaskEntity.class, TaskChangeEntity.class, ExternalUserEntity.class, PlaceEntity.class, TravelEntity.class, ShareResourceEntity.class, ExternalSourceEntity.class, AccessGrantEntity.class, AccessRequestEntity.class, SyncStateEntity.class, TaskOverlayEntity.class, PendingOutboxEntity.class, ReceivedInboxEntity.class}, version = 1, exportSchema = true)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "thoth_tasks.db").fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }

    public abstract UserDao userDao();

    public abstract TaskDao taskDao();

    public abstract TaskChangeDao taskChangeDao();

    public abstract ShareResourceDao shareResourceDao();

    public abstract ExternalSourceDao externalSourceDao();

    public abstract AccessGrantDao accessGrantDao();

    public abstract AccessRequestDao accessRequestDao();

    public abstract SyncStateDao syncStateDao();

    public abstract OutboxDao outboxDao();

    public abstract InboxDao inboxDao();

    public abstract ExternalUserDao externalUserDao();

    public abstract PlaceDao placeDao();

    public abstract TravelDao travelDao();

    public abstract TaskOverlayDao taskOverlayDao(); // ya lo usas
}