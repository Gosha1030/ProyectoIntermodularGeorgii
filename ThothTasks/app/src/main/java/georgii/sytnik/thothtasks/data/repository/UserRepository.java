package georgii.sytnik.thothtasks.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import georgii.sytnik.thothtasks.data.db.DatabaseContract.UserTable;
import georgii.sytnik.thothtasks.data.db.ThothDbHelper;
import georgii.sytnik.thothtasks.data.enumtype.UserType;
import georgii.sytnik.thothtasks.data.model.AppUser;

public class UserRepository {

    private final ThothDbHelper dbHelper;

    public UserRepository(Context context) {
        dbHelper = new ThothDbHelper(context);
    }

    public long insert(AppUser user) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(UserTable.DEFAULT_TASK_GROUP_ID, user.getDefaultTaskGroupId());
        cv.put(UserTable.USER_NAME, user.getUserName());
        cv.put(UserTable.PASSWORD, user.getPassword());
        cv.put(UserTable.TYPE, user.getType().name());
        cv.put(UserTable.IP, user.getIp());
        cv.put(UserTable.PASSWORD_REQUIRED, user.isPasswordRequired() ? 1 : 0);
        cv.put(UserTable.CONFIRM_REQUIRED, user.isConfirmRequired() ? 1 : 0);
        cv.put(UserTable.PORT, user.getPort());
        cv.put(UserTable.SETTINGS_JSON, user.getSettingsJson());
        return db.insert(UserTable.TABLE, null, cv);
    }

    public List<AppUser> getAll() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<AppUser> result = new ArrayList<>();

        Cursor c = db.query(UserTable.TABLE, null, null, null, null, null,
                UserTable.USER_NAME + " ASC");

        try {
            while (c.moveToNext()) {
                AppUser user = new AppUser();
                user.setUserId(c.getLong(c.getColumnIndexOrThrow(UserTable.USER_ID)));

                int groupIdx = c.getColumnIndexOrThrow(UserTable.DEFAULT_TASK_GROUP_ID);
                user.setDefaultTaskGroupId(c.isNull(groupIdx) ? null : c.getLong(groupIdx));

                user.setUserName(c.getString(c.getColumnIndexOrThrow(UserTable.USER_NAME)));
                user.setPassword(c.getString(c.getColumnIndexOrThrow(UserTable.PASSWORD)));
                user.setType(UserType.valueOf(c.getString(c.getColumnIndexOrThrow(UserTable.TYPE))));

                int ipIdx = c.getColumnIndexOrThrow(UserTable.IP);
                user.setIp(c.isNull(ipIdx) ? null : c.getInt(ipIdx));

                user.setPasswordRequired(c.getInt(c.getColumnIndexOrThrow(UserTable.PASSWORD_REQUIRED)) == 1);
                user.setConfirmRequired(c.getInt(c.getColumnIndexOrThrow(UserTable.CONFIRM_REQUIRED)) == 1);

                int portIdx = c.getColumnIndexOrThrow(UserTable.PORT);
                user.setPort(c.isNull(portIdx) ? null : c.getInt(portIdx));

                int settingsIdx = c.getColumnIndexOrThrow(UserTable.SETTINGS_JSON);
                user.setSettingsJson(c.isNull(settingsIdx) ? null : c.getString(settingsIdx));

                result.add(user);
            }
        } finally {
            c.close();
        }

        return result;
    }

    public List<AppUser> getLocalAndNormalUsers() {
        List<AppUser> all = getAll();
        List<AppUser> filtered = new ArrayList<>();
        for (AppUser user : all) {
            if (user.getType() == UserType.LOCAL || user.getType() == UserType.USER) {
                filtered.add(user);
            }
        }
        return filtered;
    }

    public AppUser getById(long userId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(
                UserTable.TABLE,
                null,
                UserTable.USER_ID + "=?",
                new String[]{String.valueOf(userId)},
                null,
                null,
                null
        );

        try {
            if (c.moveToFirst()) {
                AppUser user = new AppUser();
                user.setUserId(c.getLong(c.getColumnIndexOrThrow(UserTable.USER_ID)));

                int groupIdx = c.getColumnIndexOrThrow(UserTable.DEFAULT_TASK_GROUP_ID);
                user.setDefaultTaskGroupId(c.isNull(groupIdx) ? null : c.getLong(groupIdx));

                user.setUserName(c.getString(c.getColumnIndexOrThrow(UserTable.USER_NAME)));
                user.setPassword(c.getString(c.getColumnIndexOrThrow(UserTable.PASSWORD)));
                user.setType(UserType.valueOf(c.getString(c.getColumnIndexOrThrow(UserTable.TYPE))));

                int ipIdx = c.getColumnIndexOrThrow(UserTable.IP);
                user.setIp(c.isNull(ipIdx) ? null : c.getInt(ipIdx));

                user.setPasswordRequired(c.getInt(c.getColumnIndexOrThrow(UserTable.PASSWORD_REQUIRED)) == 1);
                user.setConfirmRequired(c.getInt(c.getColumnIndexOrThrow(UserTable.CONFIRM_REQUIRED)) == 1);

                int portIdx = c.getColumnIndexOrThrow(UserTable.PORT);
                user.setPort(c.isNull(portIdx) ? null : c.getInt(portIdx));

                int settingsIdx = c.getColumnIndexOrThrow(UserTable.SETTINGS_JSON);
                user.setSettingsJson(c.isNull(settingsIdx) ? null : c.getString(settingsIdx));
                return user;
            }
            return null;
        } finally {
            c.close();
        }
    }
}