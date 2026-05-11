package georgii.sytnik.thothtasks.domain.action;

import android.content.Context;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.TaskOverlayEntity;
import georgii.sytnik.thothtasks.db.entities.TravelEntity;
import georgii.sytnik.thothtasks.domain.schedule.DayTimelinePlanner;
import georgii.sytnik.thothtasks.domain.schedule.OccurrenceEngine;
import georgii.sytnik.thothtasks.domain.schedule.TaskCollector;
import georgii.sytnik.thothtasks.domain.schedule.TaskWithSource;
import georgii.sytnik.thothtasks.ui.schedule.DayBlock;

import static georgii.sytnik.thothtasks.domain.action.ActionKeys.*;

public final class DayActionPlan {

    private DayActionPlan() {}

    public static void buildAndScheduleDay(Context ctx, AppDatabase db, Calendar dayLocal00) {
        // dayLocal00 must already be at 00:00 local
        Calendar day = (Calendar) dayLocal00.clone();

        // Collect tasks for scheduler (with sourceId for overlays, and respects IncludedInSchedule)
        byte[] rootId = db.userDao().findById(georgii.sytnik.thothtasks.security.SessionStore.loadLastUserId(ctx)).taskRoot;
        List<TaskWithSource> all = TaskCollector.collect(db, rootId);

        // start times for OccurrenceEngine
        HashMap<String, Long> startMap = new HashMap<>();
        for (TaskWithSource tws : all) {
            TaskEntity t = tws.task;
            TaskChangeEntity create = db.taskChangeDao().findCreateTask(t.taskId);
            long startUtc = (create != null && create.whenApplyUtcMs != null)
                    ? create.whenApplyUtcMs
                    : (create != null ? create.createAtUtcMs : System.currentTimeMillis());
            startMap.put(hex(t.taskId), startUtc);
        }

        // tasks active today
        List<TaskWithSource> activeToday = new ArrayList<>();
        List<TaskWithSource> activeNoTime = new ArrayList<>();

        for (TaskWithSource tws : all) {
            TaskEntity t = tws.task;
            long sUtc = startMap.get(hex(t.taskId));
            if (!OccurrenceEngine.isActiveOnDay(t, sUtc, day)) continue;

            if (t.startTimeMin == null || t.finishTimeMin == null) activeNoTime.add(tws);
            else activeToday.add(tws);
        }

        // --- Visible blocks (TASK) using your priority rules ---
        List<TaskEntity> todayForPlanner = new ArrayList<>();
        HashMap<String, TaskWithSource> byId = new HashMap<>();

        for (TaskWithSource tws : activeToday) {
            TaskEntity t = tws.task;
            // apply muted overlay not needed for action planning
            todayForPlanner.add(t);
            byId.put(hex(t.taskId), tws);
        }

        Map<String, Integer> depth = DayTimelinePlanner.computeDepths(todayForPlanner);
        List<DayBlock> visibleTaskBlocks = DayTimelinePlanner.buildBlocks(todayForPlanner, depth);

        // assign taskIdHex into blocks already produced by your planner? (your DayBlock has taskIdHex in our updated version)
        // If your buildBlocks already sets taskIdHex, ignore.
        // else you'd need a custom build that uses taskIdHex.

        // Build travel blocks (same logic as scheduler; simplified: only if travel exists and gap allows)
        List<DayBlock> travelBlocks = buildTravelBlocks(db, visibleTaskBlocks, byId);

        // Merge for "inicio real": if a TRAVEL ends exactly when TASK starts => use travel start
        travelBlocks.sort(Comparator.comparingInt(a -> a.startMin));
        visibleTaskBlocks.sort(Comparator.comparingInt(a -> a.startMin));

        // Build quick map: taskStartMin -> travelStartMin (if travel ends at taskStart)
        HashMap<Integer, Integer> travelStartByTaskStart = new HashMap<>();
        for (DayBlock tr : travelBlocks) {
            int trEnd = tr.endMin;
            for (DayBlock tb : visibleTaskBlocks) {
                if (tb.startMin == trEnd) {
                    travelStartByTaskStart.put(tb.startMin, tr.startMin);
                }
            }
        }

        // Schedule actions for timed tasks (per visible block occurrence)
        String dayKey = new SimpleDateFormat("yyyyMMdd", Locale.US).format(day.getTime());

        for (DayBlock b : visibleTaskBlocks) {
            TaskWithSource tws = byId.get(b.taskIdHex);
            if (tws == null) continue;

            TaskEntity t = tws.task;

            // Read actions (overlay for imported, else base)
            JSONObject actions = resolveActions(db, tws.sourceId, t);

            int startRealMin = travelStartByTaskStart.getOrDefault(b.startMin, b.startMin);
            long startRealUtc = day00ToUtc(day) + startRealMin * 60_000L;

            // notify_on_day
            if (actions.optBoolean(NOTIFY_ON_DAY, false)) {
                scheduleNotifyAt(ctx, dayKey, t, startRealUtc, "ON_DAY_START", "Notify on day: " + t.taskName);
            }

            if (actions.optBoolean(ALARM, false)) {
                scheduleAlarmAt(ctx, dayKey, t, startRealUtc, "START", "Alarm: " + t.taskName);
            }

            if (actions.optBoolean(NOTIFY_1H, false)) {
                scheduleNotifyAt(ctx, dayKey, t, startRealUtc - 60*60_000L, "MINUS_60M", "1h: " + t.taskName);
            }
            if (actions.optBoolean(NOTIFY_10M, false)) {
                scheduleNotifyAt(ctx, dayKey, t, startRealUtc - 10*60_000L, "MINUS_10M", "10m: " + t.taskName);
            }
            if (actions.optBoolean(NOTIFY_1M, false)) {
                scheduleNotifyAt(ctx, dayKey, t, startRealUtc - 1*60_000L, "MINUS_1M", "1m: " + t.taskName);
            }

            // notify_day/week/month relative to this occurrence day at 00:00
            long day00Utc = day00ToUtc(day);
            if (actions.optBoolean(NOTIFY_DAY, false))
                scheduleNotifyAt(ctx, dayKey, t, day00Utc - 24*60*60_000L, "DAY_BEFORE_00", "1 día antes: " + t.taskName);

            if (actions.optBoolean(NOTIFY_WEEK, false))
                scheduleNotifyAt(ctx, dayKey, t, day00Utc - 7L*24*60*60_000L, "WEEK_BEFORE_00", "1 semana antes: " + t.taskName);

            if (actions.optBoolean(NOTIFY_MONTH, false)) {
                Calendar m = (Calendar) day.clone();
                m.add(Calendar.MONTH, -1);
                long m00 = day00ToUtc(m);
                scheduleNotifyAt(ctx, dayKey, t, m00, "MONTH_BEFORE_00", "1 mes antes: " + t.taskName);
            }
        }

        // Schedule actions for no-time tasks: notify_on_day at 00:00 + relative day/week/month
        for (TaskWithSource tws : activeNoTime) {
            TaskEntity t = tws.task;
            JSONObject actions = resolveActions(db, tws.sourceId, t);

            long day00Utc = day00ToUtc(day);
            if (actions.optBoolean(NOTIFY_ON_DAY, false)) scheduleNotifyAt(ctx, dayKey, t, day00Utc, "ON_DAY_00", "Notify (día): " + t.taskName);
            if (actions.optBoolean(NOTIFY_DAY, false))
                scheduleNotifyAt(ctx, dayKey, t, day00Utc - 24*60*60_000L, "DAY_BEFORE_00", "1 día antes: " + t.taskName);
            if (actions.optBoolean(NOTIFY_WEEK, false))
                scheduleNotifyAt(ctx, dayKey, t, day00Utc - 7L*24*60*60_000L, "WEEK_BEFORE_00", "1 semana antes: " + t.taskName);
            if (actions.optBoolean(NOTIFY_MONTH, false)) {
                Calendar m = (Calendar) day.clone();
                m.add(Calendar.MONTH, -1);
                long m00 = day00ToUtc(m);
                scheduleNotifyAt(ctx, dayKey, t, m00, "MONTH_BEFORE_00", "1 mes antes: " + t.taskName);
            }
        }

        // DND intervals from visible blocks (if DND enabled on that block's task)
        scheduleDndFromVisibleBlocks(ctx, db, day, dayKey, visibleTaskBlocks, byId, travelStartByTaskStart);
    }

    private static void scheduleDndFromVisibleBlocks(Context ctx, AppDatabase db, Calendar day, String dayKey,
                                                     List<DayBlock> visibleTaskBlocks,
                                                     HashMap<String, TaskWithSource> byId,
                                                     HashMap<Integer, Integer> travelStartByTaskStart) {

        // Build intervals where visible task has dnd=true
        class Interval { int s,e; Interval(int s,int e){this.s=s;this.e=e;} }
        List<Interval> on = new ArrayList<>();

        for (DayBlock b : visibleTaskBlocks) {
            TaskWithSource tws = byId.get(b.taskIdHex);
            if (tws == null) continue;
            JSONObject actions = resolveActions(db, tws.sourceId, tws.task);
            if (!actions.optBoolean(DND, false)) continue;

            int startRealMin = travelStartByTaskStart.getOrDefault(b.startMin, b.startMin);
            int s = startRealMin;
            int e = b.endMin;
            if (e > s) on.add(new Interval(s, e));
        }

        // merge intervals
        on.sort(Comparator.comparingInt(x -> x.s));
        List<Interval> merged = new ArrayList<>();
        for (Interval it : on) {
            if (merged.isEmpty()) merged.add(it);
            else {
                Interval last = merged.get(merged.size()-1);
                if (it.s <= last.e) last.e = Math.max(last.e, it.e);
                else merged.add(it);
            }
        }

        long baseUtc = day00ToUtc(day);

        int idx = 0;
        for (Interval it : merged) {
            long onUtc = baseUtc + it.s * 60_000L;
            long offUtc = baseUtc + it.e * 60_000L;

            // deterministic codes
            int codeOn = ActionPlanner.stableCode("DND_ON|" + dayKey + "|" + idx + "|" + it.s);
            int codeOff = ActionPlanner.stableCode("DND_OFF|" + dayKey + "|" + idx + "|" + it.e);

            ActionPlanner.scheduleAt(ctx, onUtc, "DND_ON", "DND ON", codeOn);
            ActionPlanner.scheduleAt(ctx, offUtc, "DND_OFF", "DND OFF", codeOff);

            idx++;
        }
    }

    private static void scheduleNotifyAt(Context ctx, String dayKey, TaskEntity t, long whenUtc, String slot, String text) {
        int code = codeFor("NOTIFY", dayKey, t.taskId, slot);
        ActionPlanner.scheduleAt(ctx, whenUtc, "NOTIFY", text, code);
    }

    private static void scheduleAlarmAt(Context ctx, String dayKey, TaskEntity t, long whenUtc, String slot, String text) {
        int code = codeFor("ALARM", dayKey, t.taskId, slot);
        ActionPlanner.scheduleAt(ctx, whenUtc, "ALARM", text, code);
    }

    private static JSONObject resolveActions(AppDatabase db, byte[] sourceIdOrNull, TaskEntity t) {
        // base
        String json = (t.actionJson != null && !t.actionJson.trim().isEmpty()) ? t.actionJson : "{}";

        // overlay for imported
        if (sourceIdOrNull != null) {
            TaskOverlayEntity ov = db.taskOverlayDao().find(sourceIdOrNull, t.taskId);
            if (ov != null && ov.actionLocalJson != null && !ov.actionLocalJson.trim().isEmpty()) {
                // overlay overrides base for keys present; simplest v1: overlay replaces entirely if present
                json = ov.actionLocalJson;
            }
        }

        try { return new JSONObject(json); } catch (Exception e) { return new JSONObject(); }
    }

    private static List<DayBlock> buildTravelBlocks(AppDatabase db, List<DayBlock> taskBlocks, HashMap<String, TaskWithSource> byId) {
        // v1: travel blocks only affect "inicio real". We'll attempt to insert if gap allows and Travel exists.
        List<DayBlock> out = new ArrayList<>();

        byte[] lastKnownPlace = null;
        class TP { DayBlock b; byte[] p; TP(DayBlock b, byte[] p){this.b=b;this.p=p;} }
        List<TP> pts = new ArrayList<>();

        for (DayBlock b : taskBlocks) {
            TaskWithSource tws = byId.get(b.taskIdHex);
            if (tws == null) { pts.add(new TP(b, lastKnownPlace)); continue; }

            byte[] eff = effectivePlaceId(db, tws.sourceId, tws.task);
            byte[] travelPlace = (eff != null) ? eff : lastKnownPlace;
            pts.add(new TP(b, travelPlace));
            if (eff != null) lastKnownPlace = eff;
        }

        for (int i = 0; i < pts.size() - 1; i++) {
            TP a = pts.get(i);
            TP b = pts.get(i + 1);

            if (a.p == null || b.p == null) continue;
            if (equalBytes(a.p, b.p)) continue;

            int gap = b.b.startMin - a.b.endMin;
            if (gap <= 0) continue;

            TravelEntity tr = db.travelDao().findByStartFinish(a.p, b.p);
            if (tr == null) continue;

            int mandatory = tr.timeM;
            if (gap < mandatory) continue;

            out.add(new DayBlock(a.b.endMin, a.b.endMin + mandatory, "Travel"));
        }

        return out;
    }

    private static byte[] effectivePlaceId(AppDatabase db, byte[] sourceIdOrNull, TaskEntity t) {
        if (sourceIdOrNull != null) {
            TaskOverlayEntity ov = db.taskOverlayDao().find(sourceIdOrNull, t.taskId);
            if (ov != null && ov.placeLocalId != null) return ov.placeLocalId;
        }
        if (t.placeId != null) return t.placeId;

        byte[] cur = t.taskFather;
        while (cur != null) {
            TaskEntity p = db.taskDao().findById(cur);
            if (p == null) break;
            if (p.placeId != null) return p.placeId;
            cur = p.taskFather;
        }
        return null;
    }

    private static boolean equalBytes(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    private static long day00ToUtc(Calendar local00) {
        // Calendar holds local timezone; getTimeInMillis is UTC epoch
        Calendar c = (Calendar) local00.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static String hex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static int codeFor(String kind, String dayKey, byte[] taskId, String slot) {
        return ActionPlanner.stableCode(kind + "|" + dayKey + "|" + hex(taskId) + "|" + slot);
    }
}