package georgii.sytnik.thothtasks.ui.schedule;

import static georgii.sytnik.thothtasks.domain.place.PlaceResolver.effectivePlaceId;
import static georgii.sytnik.thothtasks.domain.place.PlaceResolver.placeNameOrAny;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.PlaceEntity;
import georgii.sytnik.thothtasks.db.entities.TaskOverlayEntity;
import georgii.sytnik.thothtasks.db.entities.TravelEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.domain.place.PlaceResolver;
import georgii.sytnik.thothtasks.domain.travel.TravelSettings;
import georgii.sytnik.thothtasks.net.MessageCodec;
import georgii.sytnik.thothtasks.domain.schedule.DayTimelinePlanner;
import georgii.sytnik.thothtasks.domain.schedule.OccurrenceEngine;
import georgii.sytnik.thothtasks.domain.schedule.OverlayResolver;
import georgii.sytnik.thothtasks.domain.schedule.TaskCollector;
import georgii.sytnik.thothtasks.domain.schedule.TaskWithSource;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;

public class ScheduleDayFragment extends Fragment {

    private static final String ARG_DAY_UTC = "dayUtc";
    private static final String ARG_ROOT_ID = "rootIdHex";

    public static ScheduleDayFragment newInstance(long dayUtcMs, String rootIdHex) {
        ScheduleDayFragment f = new ScheduleDayFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_DAY_UTC, dayUtcMs);
        b.putString(ARG_ROOT_ID, rootIdHex);
        f.setArguments(b);
        return f;
    }

    private AppDatabase db;

    public ScheduleDayFragment() {
        super(R.layout.fragment_schedule_day);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        db = AppDatabase.get(requireContext());

        SplitDayTimelineView timeline = view.findViewById(R.id.dayTimeline);
        RecyclerView rvNoTime = view.findViewById(R.id.rvNoTime);
        rvNoTime.setLayoutManager(new LinearLayoutManager(requireContext()));

        long dayUtc = requireArguments().getLong(ARG_DAY_UTC);
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(dayUtc);
        zeroTime(day);

        new Thread(() -> {
            byte[] rootId = hexToBytes(requireArguments().getString(ARG_ROOT_ID));

            List<TaskWithSource> all = TaskCollector.collect(db, rootId);

            HashMap<String, Long> startMap = new HashMap<>();
            for (TaskWithSource tws : all) {
                TaskEntity t = tws.task;
                TaskChangeEntity create = db.taskChangeDao().findCreateTask(t.taskId);
                long startUtc = (create != null && create.whenApplyUtcMs != null)
                        ? create.whenApplyUtcMs
                        : (create != null ? create.createAtUtcMs : System.currentTimeMillis());
                startMap.put(hex(t.taskId), startUtc);
            }

            List<TaskEntity> today = new ArrayList<>();
            List<TaskLineAdapter.Line> noTime = new ArrayList<>();

            for (TaskWithSource tws : all) {
                TaskEntity t = tws.task;
                long sUtc = startMap.get(hex(t.taskId));
                if (!OccurrenceEngine.isActiveOnDay(t, sUtc, day)) continue;

                boolean effMuted = OverlayResolver.effectiveMuted(db, tws.sourceId, t.taskId, t.muted);

                if (t.startTimeMin == null || t.finishTimeMin == null) {
                    String text = "• " + t.taskName + (t.timeM != null ? (" (" + t.timeM + "m)") : "");
                    noTime.add(new TaskLineAdapter.Line(text, effMuted));
                } else {
                    TaskEntity copy = copyTask(t);
                    copy.muted = effMuted;
                    today.add(copy);
                }
            }

            Map<String, Integer> depth = DayTimelinePlanner.computeDepths(today);
            List<DayBlock> blocks = DayTimelinePlanner.buildBlocks(today, depth);
            // --- Place text for TASK blocks (UI only: show "(Cualquier lugar)" if none) ---
            HashMap<String, TaskWithSource> byId = new HashMap<>();
            for (TaskWithSource tws : all) {
                byId.put(hex(tws.task.taskId), tws);
            }

            for (DayBlock b : blocks) {
                if (b.isTravel) continue;
                TaskWithSource tws = byId.get(b.taskIdHex);
                if (tws == null) {
                    b.placeText = "(Cualquier lugar)";
                    continue;
                }
                byte[] placeId = effectivePlaceId(tws.sourceId, tws.task);
                b.placeText = placeNameOrAny(placeId);
            }

// --- Travel generation (logic only: uses "carry-over" place for travelPlace) ---
            UserEntity ownerUser = db.userDao().findById(SessionStore.loadLastUserId(requireContext()));
            TravelSettings.Params tp = (ownerUser != null) ? TravelSettings.read(ownerUser) : new TravelSettings.Params(0, 0);

            List<DayBlock> travelBlocks = new ArrayList<>();
            List<TaskLineAdapter.Line> warnings = new ArrayList<>();

            byte[] lastKnownPlace = null;

            class TravelPoint {
                DayBlock block;
                byte[] travelPlace;
                TravelPoint(DayBlock b, byte[] p) { block=b; travelPlace=p; }
            }

            List<TravelPoint> points = new ArrayList<>();

            for (DayBlock b : blocks) {
                if (b.isTravel) continue;
                TaskWithSource tws = byId.get(b.taskIdHex);
                if (tws == null) {
                    points.add(new TravelPoint(b, lastKnownPlace));
                    continue;
                }
                byte[] eff = effectivePlaceId(tws.sourceId, tws.task);
                byte[] travelPlace = (eff != null) ? eff : lastKnownPlace;
                points.add(new TravelPoint(b, travelPlace));
                if (eff != null) lastKnownPlace = eff;
            }

// Generate travel between consecutive visible tasks
            for (int i = 0; i < points.size() - 1; i++) {
                TravelPoint a = points.get(i);
                TravelPoint b = points.get(i + 1);

                if (a.travelPlace == null || b.travelPlace == null) continue;
                if (equalBytes(a.travelPlace, b.travelPlace)) continue;

                int gap = b.block.startMin - a.block.endMin;
                if (gap <= 0) continue;

                // Find travel in DB
                TravelEntity tr = db.travelDao().findByStartFinish(a.travelPlace, b.travelPlace);

                // If not exist, try auto-create based on userTimeM/googleTimeM (future) else warn
                if (tr == null) {
                    Integer base = null; // user/google not available in v1 unless you already store them somewhere
                    // If someday you store default googleTimeM in another table, you can set base here.

                    if (base == null) {
                        String sName = placeName(a.travelPlace);
                        String fName = placeName(b.travelPlace);
                        warnings.add(new TaskLineAdapter.Line(
                                getString(R.string.schedule_warn_no_travel_defined, (sName != null ? sName : getString(R.string.unknown_short)), (fName != null ? fName : getString(R.string.unknown_short)), getString(R.string.schedule_tap_to_create)),
                                false,
                                true,
                                new TravelPrefill(a.travelPlace, b.travelPlace)
                        ));
                        continue;
                    }

                    tr = new TravelEntity();
                    tr.travelId = georgii.sytnik.thothtasks.util.UuidBytes.uuidToBytes(UuidV7.newUuid());
                    tr.startPlaceId = a.travelPlace;
                    tr.finishPlaceId = b.travelPlace;
                    tr.type = null;
                    tr.userTimeM = null;
                    tr.googleTimeM = base;
                    tr.googleDataJson = null;

                    tr.timeM = base + tp.mandatoryExtraM;
                    if (tr.timeM <= 0) tr.timeM = 1;

                    db.travelDao().insert(tr);
                }

                int mandatory = tr.timeM; // already includes mandatory extra
                if (gap < mandatory) {
                    String sName = placeName(a.travelPlace);
                    String fName = placeName(b.travelPlace);
                    warnings.add(new TaskLineAdapter.Line(
                            getString(R.string.schedule_warn_no_time_for_travel, mandatory, (sName != null ? sName : getString(R.string.unknown_short)), (fName != null ? fName : getString(R.string.unknown_short))),
                            false
                    ));
                    continue; // option 1A
                }

                int optionalAdd = Math.min(tp.optionalExtraM, gap - mandatory);
                int dur = mandatory + Math.max(0, optionalAdd);

                String sName = placeName(a.travelPlace);
                String fName = placeName(b.travelPlace);
                String line3 = (sName != null ? sName : getString(R.string.unknown_short)) + " → " + (fName != null ? fName : getString(R.string.unknown_short));

                // Travel block occupies time in the gap starting at end of previous task
                travelBlocks.add(new DayBlock(a.block.endMin, a.block.endMin + dur, line3));
            }

// Merge blocks (tasks + travels) sorted by time
            List<DayBlock> allBlocks = new ArrayList<>();
            allBlocks.addAll(blocks);
            allBlocks.addAll(travelBlocks);
            allBlocks.sort((x, y) -> {
                if (x.startMin != y.startMin) return Integer.compare(x.startMin, y.startMin);
                return Integer.compare(x.endMin, y.endMin);
            });

// Add warnings under "Sin hora" list (without affecting task list)
            noTime.addAll(warnings);

// Replace blocks variable used by UI
            blocks.clear();
            blocks.addAll(allBlocks);

            for (TaskWithSource tws : all) {
                byId.put(hex(tws.task.taskId), tws);
            }

            for (DayBlock b : blocks) {
                TaskWithSource tws = byId.get(b.taskIdHex);
                if (tws == null) {
                    b.placeText = "(Cualquier lugar)";
                    continue;
                }
                byte[] placeId = PlaceResolver.effectivePlaceId(db, tws.sourceId, tws.task);
                b.placeText = PlaceResolver.placeNameOrAny(db, placeId);
            }

            requireActivity().runOnUiThread(() -> {
                timeline.setBlocks(blocks);
                rvNoTime.setAdapter(new TaskLineAdapter(noTime, line -> {
                    if (!(line.payload instanceof TravelPrefill)) return;
                    TravelPrefill tpr = (TravelPrefill) line.payload;

                    Intent i = new Intent(requireContext(), georgii.sytnik.thothtasks.ui.TravelsActivity.class);
                    i.putExtra(georgii.sytnik.thothtasks.ui.TravelsActivity.EXTRA_AUTO_OPEN, true);
                    i.putExtra(georgii.sytnik.thothtasks.ui.TravelsActivity.EXTRA_PREFILL_START, tpr.startPlaceId);
                    i.putExtra(georgii.sytnik.thothtasks.ui.TravelsActivity.EXTRA_PREFILL_FINISH, tpr.finishPlaceId);
                    startActivity(i);
                }));
            });

        }).start();
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

    private static byte[] hexToBytes(String hex) {
        if (hex == null) return null;
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
    private static TaskEntity copyTask(TaskEntity t) {
        TaskEntity c = new TaskEntity();
        c.taskFather = t.taskFather;
        c.taskName = t.taskName;
        c.type = t.type;
        c.periodD = t.periodD;
        c.daysOfJson = t.daysOfJson;
        c.periodicJson = t.periodicJson;
        c.state = t.state;
        c.startTimeMin = t.startTimeMin;
        c.finishTimeMin = t.finishTimeMin;
        c.timeM = t.timeM;
        c.uninterrupted = t.uninterrupted;
        c.weight = t.weight;
        c.actionJson = t.actionJson;
        c.muted = t.muted;
        c.placeId = t.placeId;
        return c;
    }

    private byte[] effectivePlaceId(byte[] sourceIdOrNull, TaskEntity t) {
        // overlay importada
        if (sourceIdOrNull != null) {
            TaskOverlayEntity ov = db.taskOverlayDao().find(sourceIdOrNull, t.taskId);
            if (ov != null && ov.placeLocalId != null) return ov.placeLocalId;
        }

        // place directo
        if (t.placeId != null) return t.placeId;

        // herencia dinámica
        byte[] cur = t.taskFather;
        while (cur != null) {
            TaskEntity p = db.taskDao().findById(cur);
            if (p == null) break;
            if (p.placeId != null) return p.placeId;
            cur = p.taskFather;
        }
        return null;
    }

    private String placeNameOrAny(byte[] placeId) {
        if (placeId == null) return "(Cualquier lugar)";
        PlaceEntity p = db.placeDao().findById(placeId);
        return p != null ? p.placeName : getString(R.string.unknown_short);
    }

    private String placeName(byte[] placeId) {
        if (placeId == null) return null;
        PlaceEntity p = db.placeDao().findById(placeId);
        return p != null ? p.placeName : getString(R.string.unknown_short);
    }

    private static boolean equalBytes(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    private static class TravelPrefill {
        final byte[] startPlaceId;
        final byte[] finishPlaceId;
        TravelPrefill(byte[] s, byte[] f) { startPlaceId = s; finishPlaceId = f; }
    }
}