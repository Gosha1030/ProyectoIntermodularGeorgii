package georgii.sytnik.thothtasks.domain.sync;

import static georgii.sytnik.thothtasks.util.HexBytes.hex;
import static georgii.sytnik.thothtasks.util.HexBytes.hexToBytes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.ExternalSourceEntity;
import georgii.sytnik.thothtasks.db.entities.SyncStateEntity;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.time.UuidV7;
import georgii.sytnik.thothtasks.util.UuidBytes;

/**
 * Imports remote Tasks + TaskChanges as a local copy under root:
 */
public final class Importer {

    private Importer() {
    }

    public static ImportResult importSyncResult(AppDatabase db, byte[] ownerUserId, ExternalSourceEntity source, JSONArray syncTasks, JSONArray syncChanges, long remoteVersion) throws JSONException {
        if (ownerUserId == null || source == null)
            throw new IllegalArgumentException("owner/source null");

        UserEntity owner = db.userDao().findById(ownerUserId);
        if (owner == null) throw new IllegalStateException("Owner user not found");

        byte[] importedRootId = source.importedRootTaskId;
        if (importedRootId == null) {
            importedRootId = createImportedRoot(db, owner.taskRoot, source.displayName);
            db.externalSourceDao().setImportedRoot(source.sourceId, importedRootId);
            source.importedRootTaskId = importedRootId;
        } else {
            TaskEntity existing = db.taskDao().findById(importedRootId);
            if (existing == null) {
                importedRootId = createImportedRoot(db, owner.taskRoot, source.displayName);
                db.externalSourceDao().setImportedRoot(source.sourceId, importedRootId);
                source.importedRootTaskId = importedRootId;
            }
        }

        Set<String> payloadIds = new HashSet<>();
        for (int i = 0; i < syncTasks.length(); i++) {
            JSONObject t = syncTasks.optJSONObject(i);
            if (t == null) continue;
            String taskIdHex = t.optString("taskId", "");
            if (!taskIdHex.isEmpty()) payloadIds.add(taskIdHex);
        }

        for (int i = 0; i < syncTasks.length(); i++) {
            JSONObject jt = syncTasks.optJSONObject(i);
            if (jt == null) continue;

            TaskEntity t = jsonToTaskEntity(jt);

            byte[] father = t.taskFather;
            if (father == null || !payloadIds.contains(hex(father))) {
                t.taskFather = importedRootId;
            }

            upsertTask(db, t);
        }

        long maxApplied = 0;
        for (int i = 0; i < syncChanges.length(); i++) {
            JSONObject jc = syncChanges.optJSONObject(i);
            if (jc == null) continue;

            TaskChangeEntity ch = jsonToTaskChangeEntity(jc);
            db.taskChangeDao().insertIgnore(ch);
            maxApplied = Math.max(maxApplied, ch.createAtUtcMs);
        }

        long appliedVersion = Math.max(maxApplied, remoteVersion);

        String peerKey = source.ip + ":" + source.port;
        SyncStateEntity st = db.syncStateDao().find(peerKey, source.resourceId);
        if (st == null) {
            st = new SyncStateEntity();
            st.syncId = UuidBytes.uuidToBytes(UuidV7.newUuid());
            st.peerKey = peerKey;
            st.resourceId = source.resourceId;
        }
        long now = System.currentTimeMillis();
        st.lastSeenUtcMs = now;
        st.lastSyncUtcMs = now;
        st.lastRemoteVersion = remoteVersion;
        st.lastAppliedVersion = appliedVersion;
        st.hasUpdate = false;
        st.lastError = null;
        db.syncStateDao().upsert(st);

        return new ImportResult(importedRootId, appliedVersion);
    }

    private static byte[] createImportedRoot(AppDatabase db, byte[] userTaskRootId, String displayName) {
        TaskEntity root = new TaskEntity();
        root.taskId = UuidBytes.uuidToBytes(UuidV7.newUuid());
        root.taskFather = userTaskRootId;
        root.taskName = "[Imported] " + displayName;
        root.type = "Empty";
        root.state = true;
        root.muted = false;
        root.uninterrupted = true;

        db.taskDao().insert(root);
        return root.taskId;
    }

    private static void upsertTask(AppDatabase db, TaskEntity t) {
        TaskEntity existing = db.taskDao().findById(t.taskId);
        if (existing == null) {
            db.taskDao().insert(t);
        } else {
            db.taskDao().update(t);
        }
    }

    private static TaskEntity jsonToTaskEntity(JSONObject o) throws JSONException {
        TaskEntity t = new TaskEntity();

        t.taskId = hexToBytes(o.getString("taskId"));

        Object father = o.opt("fatherId");
        if (father == null || father == JSONObject.NULL) {
            t.taskFather = null;
        } else {
            t.taskFather = hexToBytes(o.getString("fatherId"));
        }

        t.taskName = o.optString("name", "Task");
        t.type = o.optString("type", "Unique");

        if (!o.isNull("start")) t.startTimeMin = o.optInt("start");
        if (!o.isNull("finish")) t.finishTimeMin = o.optInt("finish");
        if (!o.isNull("timeM")) t.timeM = o.optInt("timeM");

        String daysOf = o.optString("daysOf", null);
        t.daysOfJson = (daysOf != null && daysOf.trim().isEmpty()) ? null : daysOf;

        String periodic = o.optString("periodic", null);
        t.periodicJson = (periodic != null && periodic.trim().isEmpty()) ? null : periodic;

        if (!o.isNull("periodD")) t.periodD = o.optInt("periodD");
        if (!o.isNull("weight")) t.weight = o.optInt("weight");

        t.state = true;
        t.muted = false;
        t.placeId = null;
        t.actionJson = null;
        t.uninterrupted = true;

        return t;
    }

    private static TaskChangeEntity jsonToTaskChangeEntity(JSONObject o) throws JSONException {
        TaskChangeEntity c = new TaskChangeEntity();
        c.taskChangeId = hexToBytes(o.getString("id"));
        c.taskId = hexToBytes(o.getString("taskId"));

        Object newTask = o.opt("newTaskId");
        if (newTask == null || newTask == JSONObject.NULL) {
            c.newTaskId = null;
        } else {
            c.newTaskId = hexToBytes(o.getString("newTaskId"));
        }

        c.type = o.optString("type", "create_task");
        c.createAtUtcMs = o.optLong("createAt", System.currentTimeMillis());

        if (o.isNull("whenApply")) c.whenApplyUtcMs = null;
        else c.whenApplyUtcMs = o.optLong("whenApply");

        return c;
    }

    public record ImportResult(byte[] importedRootTaskId, long appliedVersion) {
    }
}
