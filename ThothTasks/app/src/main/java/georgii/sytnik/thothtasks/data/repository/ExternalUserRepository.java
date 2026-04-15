package georgii.sytnik.thothtasks.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import georgii.sytnik.thothtasks.data.db.DatabaseContract.ExternalUserTable;
import georgii.sytnik.thothtasks.data.db.ThothDbHelper;
import georgii.sytnik.thothtasks.data.model.ExternalUser;

public class ExternalUserRepository {

    private final ThothDbHelper dbHelper;

    public ExternalUserRepository(Context context) {
        dbHelper = new ThothDbHelper(context);
    }

    public long insert(ExternalUser user) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(ExternalUserTable.USER_ID, user.getUserId());
        cv.put(ExternalUserTable.EXTERNAL_USER_NAME, user.getExternalUserName());
        cv.put(ExternalUserTable.IP, user.getIp());
        cv.put(ExternalUserTable.PORT, user.getPort());
        cv.put(ExternalUserTable.TYPE, user.getType());
        return db.insert(ExternalUserTable.TABLE, null, cv);
    }

    public List<ExternalUser> getAll() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<ExternalUser> result = new ArrayList<>();

        Cursor c = db.query(ExternalUserTable.TABLE, null, null, null, null, null,
                ExternalUserTable.EXTERNAL_USER_NAME + " ASC");

        try {
            while (c.moveToNext()) {
                ExternalUser item = new ExternalUser();
                item.setExternalId(c.getLong(c.getColumnIndexOrThrow(ExternalUserTable.EXTERNAL_ID)));
                item.setUserId(c.getLong(c.getColumnIndexOrThrow(ExternalUserTable.USER_ID)));
                item.setExternalUserName(c.getString(c.getColumnIndexOrThrow(ExternalUserTable.EXTERNAL_USER_NAME)));
                item.setIp(c.getInt(c.getColumnIndexOrThrow(ExternalUserTable.IP)));
                item.setPort(c.getInt(c.getColumnIndexOrThrow(ExternalUserTable.PORT)));

                int typeIdx = c.getColumnIndexOrThrow(ExternalUserTable.TYPE);
                item.setType(c.isNull(typeIdx) ? null : c.getString(typeIdx));

                result.add(item);
            }
        } finally {
            c.close();
        }

        return result;
    }

    public List<ExternalUser> getByUserId(long userId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<ExternalUser> result = new ArrayList<>();

        Cursor c = db.query(
                ExternalUserTable.TABLE,
                null,
                ExternalUserTable.USER_ID + "=?",
                new String[]{String.valueOf(userId)},
                null,
                null,
                ExternalUserTable.EXTERNAL_USER_NAME + " ASC"
        );

        try {
            while (c.moveToNext()) {
                ExternalUser item = new ExternalUser();
                item.setExternalId(c.getLong(c.getColumnIndexOrThrow(ExternalUserTable.EXTERNAL_ID)));
                item.setUserId(c.getLong(c.getColumnIndexOrThrow(ExternalUserTable.USER_ID)));
                item.setExternalUserName(c.getString(c.getColumnIndexOrThrow(ExternalUserTable.EXTERNAL_USER_NAME)));
                item.setIp(c.getInt(c.getColumnIndexOrThrow(ExternalUserTable.IP)));
                item.setPort(c.getInt(c.getColumnIndexOrThrow(ExternalUserTable.PORT)));

                int typeIdx = c.getColumnIndexOrThrow(ExternalUserTable.TYPE);
                item.setType(c.isNull(typeIdx) ? null : c.getString(typeIdx));

                result.add(item);
            }
        } finally {
            c.close();
        }

        return result;
    }
}