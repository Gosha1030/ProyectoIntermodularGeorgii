package georgii.sytnik.thothtasks.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import georgii.sytnik.thothtasks.data.db.DatabaseContract.TaskGroupTable;
import georgii.sytnik.thothtasks.data.db.ThothDbHelper;
import georgii.sytnik.thothtasks.data.model.TaskGroup;

public class TaskGroupRepository {

    private final ThothDbHelper dbHelper;

    public TaskGroupRepository(Context context) {
        dbHelper = new ThothDbHelper(context);
    }

    public long insert(TaskGroup group) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(TaskGroupTable.TASK_GROUP_NAME, group.getTaskGroupName());
        cv.put(TaskGroupTable.PARENT_TASK_GROUP_ID, group.getParentTaskGroupId());
        cv.put(TaskGroupTable.USER_ID, group.getUserId());
        cv.put(TaskGroupTable.STATE, group.isState() ? 1 : 0);
        cv.put(TaskGroupTable.WEIGHT, group.getWeight());
        cv.put(TaskGroupTable.MUTED, group.isMuted() ? 1 : 0);
        return db.insert(TaskGroupTable.TABLE, null, cv);
    }

    public List<TaskGroup> getAll() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<TaskGroup> result = new ArrayList<>();

        Cursor c = db.query(
                TaskGroupTable.TABLE,
                null,
                null,
                null,
                null,
                null,
                TaskGroupTable.WEIGHT + " ASC"
        );

        try {
            while (c.moveToNext()) {
                TaskGroup g = new TaskGroup();
                g.setTaskGroupId(c.getLong(c.getColumnIndexOrThrow(TaskGroupTable.TASK_GROUP_ID)));

                int parentIdx = c.getColumnIndexOrThrow(TaskGroupTable.PARENT_TASK_GROUP_ID);
                g.setParentTaskGroupId(c.isNull(parentIdx) ? null : c.getLong(parentIdx));

                int userIdx = c.getColumnIndexOrThrow(TaskGroupTable.USER_ID);
                g.setUserId(c.isNull(userIdx) ? null : c.getLong(userIdx));

                g.setTaskGroupName(c.getString(c.getColumnIndexOrThrow(TaskGroupTable.TASK_GROUP_NAME)));
                g.setState(c.getInt(c.getColumnIndexOrThrow(TaskGroupTable.STATE)) == 1);
                g.setWeight(c.getInt(c.getColumnIndexOrThrow(TaskGroupTable.WEIGHT)));
                g.setMuted(c.getInt(c.getColumnIndexOrThrow(TaskGroupTable.MUTED)) == 1);

                result.add(g);
            }
        } finally {
            c.close();
        }

        return result;
    }

    public List<TaskGroup> getRootGroups() {
        List<TaskGroup> roots = new ArrayList<>();
        for (TaskGroup g : getAll()) {
            if (g.getParentTaskGroupId() == null) {
                roots.add(g);
            }
        }
        return roots;
    }
}