package georgii.sytnik.thothtasks.domain.sync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.ExternalSourceEntity;
import georgii.sytnik.thothtasks.db.entities.SyncStateEntity;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.net.MessageCodec;
import georgii.sytnik.thothtasks.time.UuidV7;

/**
 * Imports remote Tasks + TaskChanges as a local copy under:
 *   User.taskRoot -> [Imported] <source.displayName>
 *
 * Decision A: keep remote TaskId as local TaskId (UUIDv7 collision practically impossible unless DB copied).
 *
 * Notes:
 * - Does NOT import overlays (mute/action). Those are local TaskOverlay entries.
 * - Reparents tasks whose father is null or missing from payload under imported root.
 */
public final class Importer {

    private Importer() {}

    public static class ImportResult {
        public final byte[] importedRootTaskId;
        public final long appliedVersion;
        public ImportResult(byte[] importedRootTaskId, long appliedVersion) {
            this.importedRootTaskId = importedRootTaskId;
            this.appliedVersion = appliedVersion;
        }
    }

    /**
     * @param db Room DB
     * @param ownerUserId current logged-in user (consumer)
     * @param source ExternalSource row (consumer)
     * @param syncTasks JSONArray of task JSON objects
     * @param syncChanges JSONArray of taskChange JSON objects
     * @param remoteVersion remoteVersion from SYNC_DONE (max remote TaskChange.createAt)
     */
    public static ImportResult importSyncResult(
            AppDatabase db,
            byte[] ownerUserId,
            ExternalSourceEntity source,
            JSONArray syncTasks,
            JSONArray syncChanges,
            long remoteVersion
    ) throws JSONException {
        if (ownerUserId == null || source == null) throw new IllegalArgumentException("owner/source null");

        UserEntity owner = db.userDao().findById(ownerUserId);
        if (owner == null) throw new IllegalStateException("Owner user not found");

        // 1) Ensure imported root exists
        byte[] importedRootId = source.importedRootTaskId;
        if (importedRootId == null) {
            importedRootId = createImportedRoot(db, owner.taskRoot, source.displayName);
            db.externalSourceDao().setImportedRoot(source.sourceId, importedRootId);
            source.importedRootTaskId = importedRootId;
        } else {
            // ensure it still exists; if not, recreate
            TaskEntity existing = db.taskDao().findById(importedRootId);
            if (existing == null) {
                importedRootId = createImportedRoot(db, owner.taskRoot, source.displayName);
                db.externalSourceDao().setImportedRoot(source.sourceId, importedRootId);
                source.importedRootTaskId = importedRootId;
            }
        }

        // 2) Build set of taskIds contained in this payload (to detect missing fathers)
        Set<String> payloadIds = new HashSet<>();
        for (int i = 0; i < syncTasks.length(); i++) {
            JSONObject t = syncTasks.optJSONObject(i);
            if (t == null) continue;
            String taskIdHex = t.optString("taskId", "");
            if (!taskIdHex.isEmpty()) payloadIds.add(taskIdHex);
        }

        // 3) Upsert tasks
        for (int i = 0; i < syncTasks.length(); i++) {
            JSONObject jt = syncTasks.optJSONObject(i);
            if (jt == null) continue;

            TaskEntity t = jsonToTaskEntity(jt);

            // attach to imported root if father missing or null
            byte[] father = t.taskFather;
            if (father == null || !payloadIds.contains(MessageCodec.hex(father))) {
                t.taskFather = importedRootId;
            }

            // Important: keep remote taskId as is
            upsertTask(db, t);
        }

        // 4) Insert task changes (IGNORE duplicates)
        long maxApplied = 0;
        for (int i = 0; i < syncChanges.length(); i++) {
            JSONObject jc = syncChanges.optJSONObject(i);
            if (jc == null) continue;

            TaskChangeEntity ch = jsonToTaskChangeEntity(jc);

            // We keep remote ids too (taskChangeId, taskId, newTaskId are remote ids).
            // Insert IGNORE to avoid duplicates on resync.
            db.taskChangeDao().insertIgnore(ch);

            maxApplied = Math.max(maxApplied, ch.createAtUtcMs);
        }

        long appliedVersion = Math.max(maxApplied, remoteVersion);

        // 5) Update SyncState (peerKey based: ip:port + resourceId)
        String peerKey = source.ip + ":" + source.port;
        SyncStateEntity st = db.syncStateDao().find(peerKey, source.resourceId);
        if (st == null) {
            st = new SyncStateEntity();
            st.syncId = uuidToBytes(UuidV7.newUuid());
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
        root.taskId = uuidToBytes(UuidV7.newUuid());
        root.taskFather = userTaskRootId;          // ✅ under TaskRoot (your choice A)
        root.taskName = "[Imported] " + displayName;
        root.type = "Empty";
        root.state = true;
        root.muted = false;
        root.uninterrupted = true;
        // everything else null

        db.taskDao().insert(root);
        return root.taskId;
    }

    private static void upsertTask(AppDatabase db, TaskEntity t) {
        TaskEntity existing = db.taskDao().findById(t.taskId);
        if (existing == null) {
            db.taskDao().insert(t);
        } else {
            // Keep current local-only fields? For imported tasks we treat the remote copy as authoritative base.
            // Local-only overlays are in TaskOverlay, so safe to overwrite base fields.
            db.taskDao().update(t);
        }
    }

    // ------------ JSON mapping ------------

    private static TaskEntity jsonToTaskEntity(JSONObject o) throws JSONException {
        TaskEntity t = new TaskEntity();

        t.taskId = MessageCodec.hexToBytes(o.getString("taskId"));

        Object father = o.opt("fatherId");
        if (father == null || father == JSONObject.NULL) {
            t.taskFather = null;
        } else {
            t.taskFather = MessageCodec.hexToBytes(o.getString("fatherId"));
        }

        t.taskName = o.optString("name", "Task");
        t.type = o.optString("type", "Unique");

        // Integers may come as null
        if (!o.isNull("start")) t.startTimeMin = o.optInt("start");
        if (!o.isNull("finish")) t.finishTimeMin = o.optInt("finish");
        if (!o.isNull("timeM")) t.timeM = o.optInt("timeM");

        // JSON strings
        String daysOf = o.optString("daysOf", null);
        t.daysOfJson = (daysOf != null && daysOf.trim().isEmpty()) ? null : daysOf;

        String periodic = o.optString("periodic", null);
        t.periodicJson = (periodic != null && periodic.trim().isEmpty()) ? null : periodic;

        if (!o.isNull("periodD")) t.periodD = o.optInt("periodD");
        if (!o.isNull("weight")) t.weight = o.optInt("weight");

        // Imported tasks must respect remote state/muted if provided; if not, defaults:
        // (Your mapper currently does not send state/muted; so we default to true/false)
        t.state = true;
        t.muted = false;

        // Place/Action not included in v1 payload; keep null
        t.placeId = null;
        t.actionJson = null;

        // Uninterrupted default
        t.uninterrupted = true;

        return t;
    }

    private static TaskChangeEntity jsonToTaskChangeEntity(JSONObject o) throws JSONException {
        TaskChangeEntity c = new TaskChangeEntity();
        c.taskChangeId = MessageCodec.hexToBytes(o.getString("id"));
        c.taskId = MessageCodec.hexToBytes(o.getString("taskId"));

        Object newTask = o.opt("newTaskId");
        if (newTask == null || newTask == JSONObject.NULL) {
            c.newTaskId = null;
        } else {
            c.newTaskId = MessageCodec.hexToBytes(o.getString("newTaskId"));
        }

        c.type = o.optString("type", "create_task");
        c.createAtUtcMs = o.optLong("createAt", System.currentTimeMillis());

        if (o.isNull("whenApply")) c.whenApplyUtcMs = null;
        else c.whenApplyUtcMs = o.optLong("whenApply");

        return c;
    }

    private static byte[] uuidToBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        return new byte[] {
                (byte)(msb >>> 56), (byte)(msb >>> 48), (byte)(msb >>> 40), (byte)(msb >>> 32),
                (byte)(msb >>> 24), (byte)(msb >>> 16), (byte)(msb >>>  8), (byte)(msb),
                (byte)(lsb >>> 56), (byte)(lsb >>> 48), (byte)(lsb >>> 40), (byte)(lsb >>> 32),
                (byte)(lsb >>> 24), (byte)(lsb >>> 16), (byte)(lsb >>>  8), (byte)(lsb)
        };
    }
}
