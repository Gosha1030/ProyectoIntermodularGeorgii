package georgii.sytnik.thothtasks.domain.schedule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.ShareResourceEntity;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.ui.schedule.DayBlock;

public final class ScheduleSummaryBuilder {

    private ScheduleSummaryBuilder() {}

    public static JSONObject buildFixedSummary(AppDatabase db, byte[] resourceId, long startDayUtcMs, int days) throws JSONException {

        ShareResourceEntity res = db.shareResourceDao().findById(resourceId);
        if (res == null || !res.active) {
            JSONObject o = new JSONObject();
            o.put("startDayUtcMs", startDayUtcMs);
            o.put("daysCount", days);
            o.put("days", new JSONArray());
            return o;
        }

        // TaskCollector -> TaskWithSource; unwrap tasks
        List<TaskWithSource> allWS = TaskCollector.collect(db, res.rootTaskId);
        List<TaskEntity> all = new ArrayList<>();
        for (TaskWithSource tws : allWS) all.add(tws.task);

        HashMap<String, Long> startMap = new HashMap<>();
        for (TaskEntity t : all) {
            TaskChangeEntity create = db.taskChangeDao().findCreateTask(t.taskId);
            long startUtc = (create != null && create.whenApplyUtcMs != null)
                    ? create.whenApplyUtcMs
                    : (create != null ? create.createAtUtcMs : System.currentTimeMillis());
            startMap.put(hex(t.taskId), startUtc);
        }

        List<TaskEntity> fixed = new ArrayList<>();
        for (TaskEntity t : all) {
            if (!"Empty".equals(t.type) && t.startTimeMin != null && t.finishTimeMin != null) {
                fixed.add(t);
            }
        }

        Map<String, Integer> depth = DayTimelinePlanner.computeDepths(fixed);

        JSONObject out = new JSONObject();
        out.put("startDayUtcMs", startDayUtcMs);
        out.put("daysCount", days);

        JSONArray daysArr = new JSONArray();

        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(startDayUtcMs);
        zeroTime(day);

        for (int i = 0; i < days; i++) {
            Calendar cur = (Calendar) day.clone();
            cur.add(Calendar.DATE, i);

            List<TaskEntity> today = new ArrayList<>();
            for (TaskEntity t : fixed) {
                long sUtc = startMap.get(hex(t.taskId));
                if (OccurrenceEngine.isActiveOnDay(t, sUtc, cur)) today.add(t);
            }

            List<DayBlock> blocks = DayTimelinePlanner.buildBlocks(today, depth);

            JSONArray blocksArr = new JSONArray();
            for (DayBlock b : blocks) {
                JSONObject bo = new JSONObject();
                bo.put("s", b.startMin);
                bo.put("e", b.endMin);
                blocksArr.put(bo);
            }

            JSONObject dayObj = new JSONObject();
            dayObj.put("offset", i);
            dayObj.put("blocks", blocksArr);
            daysArr.put(dayObj);
        }

        out.put("days", daysArr);
        return out;
    }

    private static void zeroTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private static String hex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}