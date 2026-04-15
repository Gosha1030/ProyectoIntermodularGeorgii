package georgii.sytnik.thothtasks.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import georgii.sytnik.thothtasks.data.db.DatabaseContract.TaskTable;
import georgii.sytnik.thothtasks.data.db.ThothDbHelper;
import georgii.sytnik.thothtasks.data.enumtype.TaskType;
import georgii.sytnik.thothtasks.data.model.Task;

public class TaskRepository {

    private final ThothDbHelper dbHelper;

    public TaskRepository(Context context) {
        this.dbHelper = new ThothDbHelper(context);
    }

    public long insert(Task task) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(TaskTable.TASK_GROUP_ID, task.getTaskGroupId());
        cv.put(TaskTable.TASK_NAME, task.getTaskName());
        cv.put(TaskTable.TYPE, task.getType().name());
        cv.put(TaskTable.DATE_RULE, task.getDateRule());
        cv.put(TaskTable.STATE, task.isState() ? 1 : 0);
        cv.put(TaskTable.START_TIME, task.getStartTime());
        cv.put(TaskTable.TIME_M, task.getTimeM());
        cv.put(TaskTable.PERIOD_TIME_M, task.getPeriodTimeM());
        cv.put(TaskTable.WEIGHT, task.getWeight());
        cv.put(TaskTable.NOTIFY_WHEN_HOW, task.getNotifyWhenHow());
        cv.put(TaskTable.WHEN_START, task.getWhenStart());
        cv.put(TaskTable.MUTED, task.isMuted() ? 1 : 0);
        cv.put(TaskTable.WHERE_TEXT, task.getWhere());
        return db.insert(TaskTable.TABLE, null, cv);
    }

    public List<Task> getAll() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Task> tasks = new ArrayList<>();

        Cursor c = db.query(TaskTable.TABLE, null, null, null, null, null, TaskTable.TASK_NAME + " ASC");
        try {
            while (c.moveToNext()) {
                tasks.add(fromCursor(c));
            }
        } finally {
            c.close();
        }

        return tasks;
    }

    public List<Task> getFiltered(String query, String typeText, Long groupId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Task> tasks = new ArrayList<>();

        List<String> conditions = new ArrayList<>();
        List<String> args = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) {
            conditions.add(TaskTable.TASK_NAME + " LIKE ?");
            args.add("%" + query.trim() + "%");
        }

        if (typeText != null && !typeText.equals("ALL")) {
            conditions.add(TaskTable.TYPE + "=?");
            args.add(typeText);
        }

        if (groupId != null) {
            conditions.add(TaskTable.TASK_GROUP_ID + "=?");
            args.add(String.valueOf(groupId));
        }

        String selection = conditions.isEmpty() ? null : joinConditions(conditions);
        String[] selectionArgs = args.isEmpty() ? null : args.toArray(new String[0]);

        Cursor c = db.query(
                TaskTable.TABLE,
                null,
                selection,
                selectionArgs,
                null,
                null,
                TaskTable.TASK_NAME + " ASC"
        );

        try {
            while (c.moveToNext()) {
                tasks.add(fromCursor(c));
            }
        } finally {
            c.close();
        }

        return tasks;
    }

    private String joinConditions(List<String> conditions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < conditions.size(); i++) {
            sb.append(conditions.get(i));
            if (i < conditions.size() - 1) sb.append(" AND ");
        }
        return sb.toString();
    }

    private Task fromCursor(Cursor c) {
        Task task = new Task();
        task.setTaskId(c.getLong(c.getColumnIndexOrThrow(TaskTable.TASK_ID)));

        int groupIndex = c.getColumnIndexOrThrow(TaskTable.TASK_GROUP_ID);
        task.setTaskGroupId(c.isNull(groupIndex) ? null : c.getLong(groupIndex));

        task.setTaskName(c.getString(c.getColumnIndexOrThrow(TaskTable.TASK_NAME)));
        task.setType(TaskType.valueOf(c.getString(c.getColumnIndexOrThrow(TaskTable.TYPE))));
        task.setDateRule(c.getString(c.getColumnIndexOrThrow(TaskTable.DATE_RULE)));
        task.setState(c.getInt(c.getColumnIndexOrThrow(TaskTable.STATE)) == 1);

        int startIndex = c.getColumnIndexOrThrow(TaskTable.START_TIME);
        task.setStartTime(c.isNull(startIndex) ? null : c.getString(startIndex));

        int timeIndex = c.getColumnIndexOrThrow(TaskTable.TIME_M);
        task.setTimeM(c.isNull(timeIndex) ? null : c.getInt(timeIndex));

        int periodIndex = c.getColumnIndexOrThrow(TaskTable.PERIOD_TIME_M);
        task.setPeriodTimeM(c.isNull(periodIndex) ? null : c.getInt(periodIndex));

        int weightIndex = c.getColumnIndexOrThrow(TaskTable.WEIGHT);
        task.setWeight(c.isNull(weightIndex) ? null : c.getInt(weightIndex));

        int notifyIndex = c.getColumnIndexOrThrow(TaskTable.NOTIFY_WHEN_HOW);
        task.setNotifyWhenHow(c.isNull(notifyIndex) ? null : c.getString(notifyIndex));

        int whenStartIndex = c.getColumnIndexOrThrow(TaskTable.WHEN_START);
        task.setWhenStart(c.isNull(whenStartIndex) ? null : c.getString(whenStartIndex));

        task.setMuted(c.getInt(c.getColumnIndexOrThrow(TaskTable.MUTED)) == 1);

        int whereIndex = c.getColumnIndexOrThrow(TaskTable.WHERE_TEXT);
        task.setWhere(c.isNull(whereIndex) ? null : c.getString(whereIndex));

        return task;
    }
}