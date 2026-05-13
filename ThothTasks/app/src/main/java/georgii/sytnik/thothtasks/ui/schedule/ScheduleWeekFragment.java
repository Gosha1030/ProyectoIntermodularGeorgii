package georgii.sytnik.thothtasks.ui.schedule;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.domain.schedule.OccurrenceEngine;
import georgii.sytnik.thothtasks.domain.schedule.OverlayResolver;
import georgii.sytnik.thothtasks.domain.schedule.ScheduleFilters;
import georgii.sytnik.thothtasks.domain.schedule.TaskCollector;
import georgii.sytnik.thothtasks.domain.schedule.TaskWithSource;
import georgii.sytnik.thothtasks.net.MessageCodec;

public class ScheduleWeekFragment extends Fragment {

    private static final String ARG_WEEK_UTC = "weekUtc";
    private static final String ARG_ROOT_ID = "rootIdHex";

    public static ScheduleWeekFragment newInstance(long anyDayUtcMs, String rootIdHex) {
        ScheduleWeekFragment f = new ScheduleWeekFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_WEEK_UTC, anyDayUtcMs);
        b.putString(ARG_ROOT_ID, rootIdHex);
        f.setArguments(b);
        return f;
    }

    private AppDatabase db;

    public ScheduleWeekFragment() {
        super(R.layout.fragment_schedule_week);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        db = AppDatabase.get(requireContext());

        RecyclerView rv = view.findViewById(R.id.rvWeek);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        long utc = requireArguments().getLong(ARG_WEEK_UTC);
        Calendar anchor = Calendar.getInstance();
        anchor.setTimeInMillis(utc);
        Calendar start = startOfWeek(anchor);

        new Thread(() -> {
            byte[] rootId = MessageCodec.hexToBytes(requireArguments().getString(ARG_ROOT_ID));

            // ✅ ahora TaskCollector devuelve TaskWithSource
            List<TaskWithSource> tasksAll = TaskCollector.collect(db, rootId);

            // ✅ filtro semanal (no mostrar Daily, no mostrar Empty vacía)
            List<TaskWithSource> tasks = new ArrayList<>();
            for (TaskWithSource tws : tasksAll) {
                if (ScheduleFilters.showInWeek(tws.task)) tasks.add(tws);
            }

            // start times for OccurrenceEngine
            HashMap<String, Long> startMap = new HashMap<>();
            for (TaskWithSource tws : tasks) {
                TaskEntity t = tws.task;
                TaskChangeEntity create = db.taskChangeDao().findCreateTask(t.taskId);
                long startUtc = (create != null && create.whenApplyUtcMs != null)
                        ? create.whenApplyUtcMs
                        : (create != null ? create.createAtUtcMs : System.currentTimeMillis());
                startMap.put(hex(t.taskId), startUtc);
            }

            List<WeekBlockAdapter.DayBlock> blocks = new ArrayList<>();
            SimpleDateFormat dfHeader = new SimpleDateFormat("EEE dd", Locale.getDefault());

            for (int i = 0; i < 7; i++) {
                Calendar day = (Calendar) start.clone();
                day.add(Calendar.DATE, i);

                List<TaskLineAdapter.Line> lines = new ArrayList<>();
                for (TaskWithSource tws : tasks) {
                    TaskEntity t = tws.task;
                    long sUtc = startMap.get(hex(t.taskId));
                    if (!OccurrenceEngine.isActiveOnDay(t, sUtc, day)) continue;

                    boolean effMuted = OverlayResolver.effectiveMuted(db, tws.sourceId, t.taskId, t.muted);

                    String line = getString(R.string.schedule_bullet_task, t.taskName);
                    if (t.startTimeMin != null) line += " " + getString(R.string.schedule_time_parens, minutesToText(t.startTimeMin));
                    lines.add(new TaskLineAdapter.Line(line, effMuted));
                }

                blocks.add(new WeekBlockAdapter.DayBlock(dfHeader.format(day.getTime()), (Calendar) day.clone(), lines));
            }

            requireActivity().runOnUiThread(() -> {
                ScheduleNavigator nav = (ScheduleNavigator) requireActivity();
                rv.setAdapter(new WeekBlockAdapter(blocks, nav));
            });
        }).start();
    }

    private static Calendar startOfWeek(Calendar any) {
        Calendar c = (Calendar) any.clone();
        int dow = c.get(Calendar.DAY_OF_WEEK);
        int diff = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
        c.add(Calendar.DATE, diff);
        zeroTime(c);
        return c;
    }

    private static void zeroTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private static String minutesToText(int min) {
        int h = min / 60;
        int m = min % 60;
        return String.format("%02d:%02d", h, m);
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}