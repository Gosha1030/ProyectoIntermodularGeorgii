package georgii.sytnik.thothtasks.domain.sync;

import static georgii.sytnik.thothtasks.util.HexBytes.hex;

import org.json.JSONException;
import org.json.JSONObject;

import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.net.MessageCodec;

public final class JsonMapper {

    private JsonMapper() {
    }

    public static JSONObject taskToJson(TaskEntity t) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("taskId", hex(t.taskId));
        o.put("fatherId", t.taskFather != null ? hex(t.taskFather) : JSONObject.NULL);
        o.put("name", t.taskName);
        o.put("type", t.type);
        o.put("start", t.startTimeMin);
        o.put("finish", t.finishTimeMin);
        o.put("timeM", t.timeM);
        o.put("daysOf", t.daysOfJson);
        o.put("periodic", t.periodicJson);
        o.put("periodD", t.periodD);
        o.put("weight", t.weight);
        return o;
    }

    public static JSONObject taskChangeToJson(TaskChangeEntity c) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", hex(c.taskChangeId));
        o.put("taskId", hex(c.taskId));
        o.put("newTaskId", c.newTaskId != null ? hex(c.newTaskId) : JSONObject.NULL);
        o.put("type", c.type);
        o.put("createAt", c.createAtUtcMs);
        o.put("whenApply", c.whenApplyUtcMs);
        return o;
    }
}