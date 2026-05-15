package georgii.sytnik.thothtasks.domain.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.ShareResourceEntity;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.domain.schedule.TaskCollector;
import georgii.sytnik.thothtasks.domain.schedule.TaskWithSource;

public final class SyncPayloadBuilder {

    private SyncPayloadBuilder() {
    }

    public static JSONObject build(AppDatabase db, byte[] resourceId, long sinceVersion) throws JSONException {

        ShareResourceEntity res = db.shareResourceDao().findById(resourceId);
        if (res == null || !res.active) return null;

        List<TaskWithSource> ws = TaskCollector.collect(db, res.rootTaskId);
        List<TaskEntity> tasks = new ArrayList<>();
        for (TaskWithSource tws : ws) tasks.add(tws.task());

        List<TaskChangeEntity> changes;
        if (sinceVersion <= 0) {
            changes = db.taskChangeDao().historyForTaskTree(res.rootTaskId);
        } else {
            changes = db.taskChangeDao().changesAfter(res.rootTaskId, sinceVersion);
        }

        long remoteVersion = 0;
        JSONArray tasksArr = new JSONArray();
        JSONArray changesArr = new JSONArray();

        for (TaskEntity t : tasks) {
            tasksArr.put(JsonMapper.taskToJson(t));
        }

        for (TaskChangeEntity c : changes) {
            changesArr.put(JsonMapper.taskChangeToJson(c));
            remoteVersion = Math.max(remoteVersion, c.createAtUtcMs);
        }

        JSONObject out = new JSONObject();
        out.put("tasks", tasksArr);
        out.put("taskChanges", changesArr);
        out.put("remoteVersion", remoteVersion);

        return out;
    }
}