package georgii.sytnik.thothtasks.domain.action;

import static georgii.sytnik.thothtasks.domain.action.ActionKeys.ALARM;
import static georgii.sytnik.thothtasks.domain.action.ActionKeys.DND;
import static georgii.sytnik.thothtasks.domain.action.ActionKeys.NOTIFY_10M;
import static georgii.sytnik.thothtasks.domain.action.ActionKeys.NOTIFY_1H;
import static georgii.sytnik.thothtasks.domain.action.ActionKeys.NOTIFY_1M;
import static georgii.sytnik.thothtasks.domain.action.ActionKeys.NOTIFY_DAY;
import static georgii.sytnik.thothtasks.domain.action.ActionKeys.NOTIFY_MONTH;
import static georgii.sytnik.thothtasks.domain.action.ActionKeys.NOTIFY_ON_DAY;
import static georgii.sytnik.thothtasks.domain.action.ActionKeys.NOTIFY_WEEK;
import static georgii.sytnik.thothtasks.util.HexBytes.equalBytes;

import android.content.Context;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import georgii.sytnik.thothtasks.R;
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
import georgii.sytnik.thothtasks.util.HexBytes;

public final class DayActionPlan {

    private DayActionPlan() {
    }

    public static void buildAndScheduleDay(Context ctx, AppDatabase db, Calendar dayLocal00) {
        Calendar day = (Calendar) dayLocal00.clone();

        byte[] rootId = db.userDao().findById(georgii.sytnik.thothtasks.security.SessionStore.loadLastUserId(ctx)).taskRoot;
        List<TaskWithSource> all = TaskCollector.collect(db, rootId);

        HashMap<String, Long> startMap = new HashMap<>();
        for (TaskWithSource tws : all) {
            TaskEntity t = tws.task();
            TaskChangeEntity create = db.taskChangeDao().findCreateTask(t.taskId);
            long startUtc = (create != null && create.whenApplyUtcMs != null) ? create.whenApplyUtcMs : (create != null ? create.createAtUtcMs : System.currentTimeMillis());
            startMap.put(HexBytes.hex(t.taskId), startUtc);
        }

        List<TaskWithSource> activeToday = new ArrayList<>();
        List<TaskWithSource> activeNoTime = new ArrayList<>();

        for (TaskWithSource tws : all) {
            TaskEntity t = tws.task();
            long sUtc = startMap.get(HexBytes.hex(t.taskId));
            if (!OccurrenceEngine.isActiveOnDay(t, sUtc, day)) continue;

            if (t.startTimeMin == null || t.finishTimeMin == null) activeNoTime.add(tws);
            else activeToday.add(tws);
        }

        List<TaskEntity> todayForPlanner = new ArrayList<>();
        HashMap<String, TaskWithSource> byId = new HashMap<>();

        for (TaskWithSource tws : activeToday) {
            TaskEntity t = tws.task();
            todayForPlanner.add(t);
            byId.put(HexBytes.hex(t.taskId), tws);
        }

        Map<String, Integer> depth = DayTimelinePlanner.computeDepths(todayForPlanner);
        List<DayBlock> visibleTaskBlocks = DayTimelinePlanner.buildBlocks(todayForPlanner, depth);

        List<DayBlock> travelBlocks = buildTravelBlocks(db, visibleTaskBlocks, byId);

        travelBlocks.sort(Comparator.comparingInt(a -> a.startMin));
        visibleTaskBlocks.sort(Comparator.comparingInt(a -> a.startMin));

        HashMap<Integer, Integer> travelStartByTaskStart = new HashMap<>();
        for (DayBlock tr : travelBlocks) {
            int trEnd = tr.endMin;
            for (DayBlock tb : visibleTaskBlocks) {
                if (tb.startMin == trEnd) {
                    travelStartByTaskStart.put(tb.startMin, tr.startMin);
                }
            }
        }

        String dayKey = new SimpleDateFormat("yyyyMMdd", Locale.US).format(day.getTime());

        for (DayBlock b : visibleTaskBlocks) {
            TaskWithSource tws = byId.get(b.taskIdHex);
            if (tws == null) continue;

            TaskEntity t = tws.task();

            JSONObject actions = resolveActions(db, tws.sourceId(), t);

            int startRealMin = travelStartByTaskStart.getOrDefault(b.startMin, b.startMin);
            long startRealUtc = day00ToUtc(day) + startRealMin * 60_000L;

            if (actions.optBoolean(NOTIFY_ON_DAY, false)) {
                scheduleNotifyAt(ctx, dayKey, t, startRealUtc, "ON_DAY_START", ctx.getString(R.string.action_msg_notify_on_day, t.taskName));
            }

            if (actions.optBoolean(ALARM, false)) {
                scheduleAlarmAt(ctx, dayKey, t, startRealUtc, "START", ctx.getString(R.string.action_msg_alarm, t.taskName));
            }

            if (actions.optBoolean(NOTIFY_1H, false)) {
                scheduleNotifyAt(ctx, dayKey, t, startRealUtc - 60 * 60_000L, "MINUS_60M", ctx.getString(R.string.action_msg_notify_1h, t.taskName));
            }
            if (actions.optBoolean(NOTIFY_10M, false)) {
                scheduleNotifyAt(ctx, dayKey, t, startRealUtc - 10 * 60_000L, "MINUS_10M", ctx.getString(R.string.action_msg_notify_10m, t.taskName));
            }
            if (actions.optBoolean(NOTIFY_1M, false)) {
                scheduleNotifyAt(ctx, dayKey, t, startRealUtc - 60_000L, "MINUS_1M", ctx.getString(R.string.action_msg_notify_1m, t.taskName));
            }

            long day00Utc = day00ToUtc(day);
            if (actions.optBoolean(NOTIFY_DAY, false))
                scheduleNotifyAt(ctx, dayKey, t, day00Utc - 24 * 60 * 60_000L, "DAY_BEFORE_00", ctx.getString(R.string.action_msg_notify_day_before, t.taskName));

            if (actions.optBoolean(NOTIFY_WEEK, false))
                scheduleNotifyAt(ctx, dayKey, t, day00Utc - 7L * 24 * 60 * 60_000L, "WEEK_BEFORE_00", ctx.getString(R.string.action_msg_notify_week_before, t.taskName));

            if (actions.optBoolean(NOTIFY_MONTH, false)) {
                Calendar m = (Calendar) day.clone();
                m.add(Calendar.MONTH, -1);
                long m00 = day00ToUtc(m);
                scheduleNotifyAt(ctx, dayKey, t, m00, "MONTH_BEFORE_00", ctx.getString(R.string.action_msg_notify_month_before, t.taskName));
            }
        }

        for (TaskWithSource tws : activeNoTime) {
            TaskEntity t = tws.task();
            JSONObject actions = resolveActions(db, tws.sourceId(), t);

            long day00Utc = day00ToUtc(day);
            if (actions.optBoolean(NOTIFY_ON_DAY, false))
                scheduleNotifyAt(ctx, dayKey, t, day00Utc, "ON_DAY_00", ctx.getString(R.string.action_msg_notify_on_day, t.taskName));
            if (actions.optBoolean(NOTIFY_DAY, false))
                scheduleNotifyAt(ctx, dayKey, t, day00Utc - 24 * 60 * 60_000L, "DAY_BEFORE_00", ctx.getString(R.string.action_msg_notify_day_before, t.taskName));
            if (actions.optBoolean(NOTIFY_WEEK, false))
                scheduleNotifyAt(ctx, dayKey, t, day00Utc - 7L * 24 * 60 * 60_000L, "WEEK_BEFORE_00", ctx.getString(R.string.action_msg_notify_week_before, t.taskName));
            if (actions.optBoolean(NOTIFY_MONTH, false)) {
                Calendar m = (Calendar) day.clone();
                m.add(Calendar.MONTH, -1);
                long m00 = day00ToUtc(m);
                scheduleNotifyAt(ctx, dayKey, t, m00, "MONTH_BEFORE_00", ctx.getString(R.string.action_msg_notify_month_before, t.taskName));
            }
        }

        scheduleDndFromVisibleBlocks(ctx, db, day, dayKey, visibleTaskBlocks, byId, travelStartByTaskStart);
    }

    private static void scheduleDndFromVisibleBlocks(Context ctx, AppDatabase db, Calendar day, String dayKey, List<DayBlock> visibleTaskBlocks, HashMap<String, TaskWithSource> byId, HashMap<Integer, Integer> travelStartByTaskStart) {

        class Interval {
            final int s;
            int e;

            Interval(int s, int e) {
                this.s = s;
                this.e = e;
            }
        }
        List<Interval> on = new ArrayList<>();

        for (DayBlock b : visibleTaskBlocks) {
            TaskWithSource tws = byId.get(b.taskIdHex);
            if (tws == null) continue;
            JSONObject actions = resolveActions(db, tws.sourceId(), tws.task());
            if (!actions.optBoolean(DND, false)) continue;

            int startRealMin = travelStartByTaskStart.getOrDefault(b.startMin, b.startMin);
            int s = startRealMin;
            int e = b.endMin;
            if (e > s) on.add(new Interval(s, e));
        }

        on.sort(Comparator.comparingInt(x -> x.s));
        List<Interval> merged = new ArrayList<>();
        for (Interval it : on) {
            if (merged.isEmpty()) merged.add(it);
            else {
                Interval last = merged.get(merged.size() - 1);
                if (it.s <= last.e) last.e = Math.max(last.e, it.e);
                else merged.add(it);
            }
        }

        long baseUtc = day00ToUtc(day);

        int idx = 0;
        for (Interval it : merged) {
            long onUtc = baseUtc + it.s * 60_000L;
            long offUtc = baseUtc + it.e * 60_000L;

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
        String json = (t.actionJson != null && !t.actionJson.trim().isEmpty()) ? t.actionJson : "{}";

        if (sourceIdOrNull != null) {
            TaskOverlayEntity ov = db.taskOverlayDao().find(sourceIdOrNull, t.taskId);
            if (ov != null && ov.actionLocalJson != null && !ov.actionLocalJson.trim().isEmpty()) {
                json = ov.actionLocalJson;
            }
        }

        try {
            return new JSONObject(json);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static List<DayBlock> buildTravelBlocks(AppDatabase db, List<DayBlock> taskBlocks, HashMap<String, TaskWithSource> byId) {
        List<DayBlock> out = new ArrayList<>();

        byte[] lastKnownPlace = null;
        class TP {
            final DayBlock b;
            final byte[] p;

            TP(DayBlock b, byte[] p) {
                this.b = b;
                this.p = p;
            }
        }
        List<TP> pts = new ArrayList<>();

        for (DayBlock b : taskBlocks) {
            TaskWithSource tws = byId.get(b.taskIdHex);
            if (tws == null) {
                pts.add(new TP(b, lastKnownPlace));
                continue;
            }

            byte[] eff = effectivePlaceId(db, tws.sourceId(), tws.task());
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

    private static long day00ToUtc(Calendar local00) {
        Calendar c = (Calendar) local00.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static int codeFor(String kind, String dayKey, byte[] taskId, String slot) {
        return ActionPlanner.stableCode(kind + "|" + dayKey + "|" + HexBytes.hex(taskId) + "|" + slot);
    }
}
