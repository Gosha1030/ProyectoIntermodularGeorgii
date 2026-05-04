package georgii.sytnik.thothtasks.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import georgii.sytnik.thothtasks.db.dao.TaskChangeDao;
import georgii.sytnik.thothtasks.db.dao.TaskDao;
import georgii.sytnik.thothtasks.db.dao.UserDao;
import georgii.sytnik.thothtasks.db.entities.ExternalUserEntity;
import georgii.sytnik.thothtasks.db.entities.PlaceEntity;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.TravelEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;

@Database(
        entities = {
                UserEntity.class,
                TaskEntity.class,
                TaskChangeEntity.class,
                ExternalUserEntity.class,
                PlaceEntity.class,
                TravelEntity.class
        },
        version = 1,
        exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract UserDao userDao();
    public abstract TaskDao taskDao();
    public abstract TaskChangeDao taskChangeDao();

    public static AppDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "thoth_tasks.db"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}