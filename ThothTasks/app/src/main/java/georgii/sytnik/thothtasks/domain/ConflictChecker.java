package georgii.sytnik.thothtasks.domain;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.domain.schedule.DayTimelinePlanner;
import georgii.sytnik.thothtasks.domain.schedule.OccurrenceEngine;
import georgii.sytnik.thothtasks.domain.schedule.TaskCollector;
import georgii.sytnik.thothtasks.domain.schedule.TaskWithSource;
import georgii.sytnik.thothtasks.ui.schedule.DayBlock;

public final class ConflictChecker {

    private ConflictChecker() {}

    /** Returns true if there is ANY overlap between external summary and local schedule blocks. */
    public static boolean hasConflictWithLocal(AppDatabase db, byte[] localRootTaskId, long startDayUtcMs, JSONObject externalSummaryBody) {

        if (externalSummaryBody == null) return true;

        long extStart = externalSummaryBody.optLong("startDayUtcMs", startDayUtcMs);
        int days = externalSummaryBody.optInt("daysCount", 30);
        JSONArray daysArr = externalSummaryBody.optJSONArray("days");
        if (daysArr == null) return true;

        // TaskCollector now returns TaskWithSource -> unwrap tasks
        List<TaskWithSource> localAllWS = TaskCollector.collect(db, localRootTaskId);
        List<TaskEntity> localAll = new ArrayList<>();
        for (TaskWithSource tws : localAllWS) localAll.add(tws.task);

        HashMap<String, Long> startMap = new HashMap<>();
        for (TaskEntity t : localAll) {
            TaskChangeEntity create = db.taskChangeDao().findCreateTask(t.taskId);
            long startUtc = (create != null && create.whenApplyUtcMs != null)
                    ? create.whenApplyUtcMs
                    : (create != null ? create.createAtUtcMs : System.currentTimeMillis());
            startMap.put(hex(t.taskId), startUtc);
        }

        // only fixed tasks for conflict (Start/Finish)
        List<TaskEntity> fixed = new ArrayList<>();
        for (TaskEntity t : localAll) {
            if (!"Empty".equals(t.type) && t.startTimeMin != null && t.finishTimeMin != null) {
                fixed.add(t);
            }
        }

        var depth = DayTimelinePlanner.computeDepths(fixed);

        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(extStart);
        zeroTime(base);

        for (int i = 0; i < days; i++) {
            JSONObject dayObj = daysArr.optJSONObject(i);
            if (dayObj == null) continue;
            JSONArray extBlocks = dayObj.optJSONArray("blocks");
            if (extBlocks == null || extBlocks.length() == 0) continue;

            Calendar day = (Calendar) base.clone();
            day.add(Calendar.DATE, i);

            List<TaskEntity> today = new ArrayList<>();
            for (TaskEntity t : fixed) {
                long sUtc = startMap.get(hex(t.taskId));
                if (OccurrenceEngine.isActiveOnDay(t, sUtc, day)) today.add(t);
            }

            List<DayBlock> localBlocks = DayTimelinePlanner.buildBlocks(today, depth);
            if (overlap(localBlocks, extBlocks)) return true;
        }

        return false;
    }

    private static boolean overlap(List<DayBlock> localBlocks, JSONArray extBlocks) {
        for (DayBlock lb : localBlocks) {
            int ls = lb.startMin;
            int le = lb.endMin;
            for (int j = 0; j < extBlocks.length(); j++) {
                JSONObject eb = extBlocks.optJSONObject(j);
                if (eb == null) continue;
                int es = eb.optInt("s", -1);
                int ee = eb.optInt("e", -1);
                if (es < 0 || ee < 0) continue;

                if (Math.max(ls, es) < Math.min(le, ee)) return true;
            }
        }
        return false;
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