package georgii.sytnik.thothtasks.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ThothDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "thoth_tasks.db";
    private static final int DB_VERSION = 1;

    public ThothDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(createUserTable());
        db.execSQL(createTaskGroupTable());
        db.execSQL(createTaskTable());
        db.execSQL(createExternalUserTable());
        db.execSQL(createTaskChangeTable());
        db.execSQL(createTravelTable());
        db.execSQL(createReassignmentTable());

        // Usuario local por defecto
        db.execSQL("INSERT INTO app_user " +
                "(user_name, password, type, password_required, confirm_required) " +
                "VALUES ('Owner', '', 'LOCAL', 0, 0)");
    }

    private String createUserTable() {
        return "CREATE TABLE app_user (" +
                "user_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "default_task_group_id INTEGER NULL, " +
                "user_name TEXT NOT NULL, " +
                "password TEXT NOT NULL DEFAULT '', " +
                "type TEXT NOT NULL, " +
                "ip INTEGER NULL, " +
                "password_required INTEGER NOT NULL DEFAULT 1, " +
                "confirm_required INTEGER NOT NULL DEFAULT 1, " +
                "port INTEGER NULL, " +
                "settings_json TEXT NULL" +
                ")";
    }

    private String createTaskGroupTable() {
        return "CREATE TABLE task_group (" +
                "task_group_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "parent_task_group_id INTEGER NULL, " +
                "user_id INTEGER NULL, " +
                "task_group_name TEXT NOT NULL, " +
                "state INTEGER NOT NULL DEFAULT 1, " +
                "weight INTEGER NOT NULL DEFAULT 0, " +
                "muted INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(parent_task_group_id) REFERENCES task_group(task_group_id) ON DELETE SET NULL, " +
                "FOREIGN KEY(user_id) REFERENCES app_user(user_id) ON DELETE SET NULL" +
                ")";
    }

    private String createTaskTable() {
        return "CREATE TABLE task (" +
                "task_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "task_group_id INTEGER NULL, " +
                "task_name TEXT NOT NULL, " +
                "type TEXT NOT NULL, " +
                "date_rule TEXT NOT NULL, " +
                "state INTEGER NOT NULL DEFAULT 1, " +
                "start_time TEXT NULL, " +
                "time_m INTEGER NULL, " +
                "period_time_m INTEGER NULL, " +
                "weight INTEGER NULL, " +
                "notify_when_how TEXT NULL, " +
                "when_start TEXT NULL, " +
                "muted INTEGER NOT NULL DEFAULT 0, " +
                "where_text TEXT NULL, " +
                "FOREIGN KEY(task_group_id) REFERENCES task_group(task_group_id) ON DELETE SET NULL" +
                ")";
    }

    private String createExternalUserTable() {
        return "CREATE TABLE external_user (" +
                "external_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "external_user_name TEXT NOT NULL, " +
                "ip INTEGER NOT NULL, " +
                "port INTEGER NOT NULL, " +
                "type TEXT NULL, " +
                "FOREIGN KEY(user_id) REFERENCES app_user(user_id) ON DELETE CASCADE" +
                ")";
    }

    private String createTaskChangeTable() {
        return "CREATE TABLE task_change (" +
                "task_change_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "task_id INTEGER NULL, " +
                "task_group_id INTEGER NULL, " +
                "type TEXT NOT NULL, " +
                "date TEXT NOT NULL, " +
                "when_at TEXT NOT NULL, " +
                "FOREIGN KEY(task_id) REFERENCES task(task_id) ON DELETE CASCADE, " +
                "FOREIGN KEY(task_group_id) REFERENCES task_group(task_group_id) ON DELETE CASCADE" +
                ")";
    }

    private String createTravelTable() {
        return "CREATE TABLE travel (" +
                "travel_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "type TEXT NOT NULL, " +
                "start_text TEXT NOT NULL, " +
                "finish_text TEXT NOT NULL, " +
                "time_m INTEGER NOT NULL" +
                ")";
    }

    private String createReassignmentTable() {
        return "CREATE TABLE id_reassignment (" +
                "reassignment_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "old_id INTEGER NOT NULL, " +
                "new_id INTEGER NOT NULL, " +
                "id_type INTEGER NOT NULL, " +
                "FOREIGN KEY(user_id) REFERENCES app_user(user_id) ON DELETE CASCADE" +
                ")";
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS id_reassignment");
        db.execSQL("DROP TABLE IF EXISTS travel");
        db.execSQL("DROP TABLE IF EXISTS task_change");
        db.execSQL("DROP TABLE IF EXISTS external_user");
        db.execSQL("DROP TABLE IF EXISTS task");
        db.execSQL("DROP TABLE IF EXISTS task_group");
        db.execSQL("DROP TABLE IF EXISTS app_user");
        onCreate(db);
    }
}