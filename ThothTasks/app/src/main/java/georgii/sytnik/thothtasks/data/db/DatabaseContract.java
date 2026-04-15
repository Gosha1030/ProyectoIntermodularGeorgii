package georgii.sytnik.thothtasks.data.db;

import android.provider.BaseColumns;

public final class DatabaseContract {

    private DatabaseContract() {}

    public static final class TaskTable implements BaseColumns {
        public static final String TABLE = "task";
        public static final String TASK_ID = "task_id";
        public static final String TASK_GROUP_ID = "task_group_id";
        public static final String TASK_NAME = "task_name";
        public static final String TYPE = "type";
        public static final String DATE_RULE = "date_rule";
        public static final String STATE = "state";
        public static final String START_TIME = "start_time";
        public static final String TIME_M = "time_m";
        public static final String PERIOD_TIME_M = "period_time_m";
        public static final String WEIGHT = "weight";
        public static final String NOTIFY_WHEN_HOW = "notify_when_how";
        public static final String WHEN_START = "when_start";
        public static final String MUTED = "muted";
        public static final String WHERE_TEXT = "where_text";
    }

    public static final class TaskGroupTable implements BaseColumns {
        public static final String TABLE = "task_group";
        public static final String TASK_GROUP_ID = "task_group_id";
        public static final String PARENT_TASK_GROUP_ID = "parent_task_group_id";
        public static final String USER_ID = "user_id";
        public static final String TASK_GROUP_NAME = "task_group_name";
        public static final String STATE = "state";
        public static final String WEIGHT = "weight";
        public static final String MUTED = "muted";
    }

    public static final class UserTable implements BaseColumns {
        public static final String TABLE = "app_user";
        public static final String USER_ID = "user_id";
        public static final String DEFAULT_TASK_GROUP_ID = "default_task_group_id";
        public static final String USER_NAME = "user_name";
        public static final String PASSWORD = "password";
        public static final String TYPE = "type";
        public static final String IP = "ip";
        public static final String PASSWORD_REQUIRED = "password_required";
        public static final String CONFIRM_REQUIRED = "confirm_required";
        public static final String PORT = "port";
        public static final String SETTINGS_JSON = "settings_json";
    }

    public static final class ExternalUserTable implements BaseColumns {
        public static final String TABLE = "external_user";
        public static final String EXTERNAL_ID = "external_id";
        public static final String USER_ID = "user_id";
        public static final String EXTERNAL_USER_NAME = "external_user_name";
        public static final String IP = "ip";
        public static final String PORT = "port";
        public static final String TYPE = "type";
    }

    public static final class TaskChangeTable implements BaseColumns {
        public static final String TABLE = "task_change";
        public static final String TASK_CHANGE_ID = "task_change_id";
        public static final String TASK_ID = "task_id";
        public static final String TASK_GROUP_ID = "task_group_id";
        public static final String TYPE = "type";
        public static final String DATE = "date";
        public static final String WHEN_AT = "when_at";
    }

    public static final class TravelTable implements BaseColumns {
        public static final String TABLE = "travel";
        public static final String TRAVEL_ID = "travel_id";
        public static final String TYPE = "type";
        public static final String START = "start_text";
        public static final String FINISH = "finish_text";
        public static final String TIME_M = "time_m";
    }

    public static final class ReassignmentTable implements BaseColumns {
        public static final String TABLE = "id_reassignment";
        public static final String REASSIGNMENT_ID = "reassignment_id";
        public static final String USER_ID = "user_id";
        public static final String OLD_ID = "old_id";
        public static final String NEW_ID = "new_id";
        public static final String ID_TYPE = "id_type";
    }
}