package georgii.sytnik.thothtasks.ui.schedule;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
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

public class ScheduleMonthFragment extends Fragment {

    private static final String ARG_MONTH_UTC = "monthUtc";
    private static final String ARG_ROOT_ID = "rootIdHex";

    public static ScheduleMonthFragment newInstance(long anyDayUtcMs, String rootIdHex) {
        ScheduleMonthFragment f = new ScheduleMonthFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_MONTH_UTC, anyDayUtcMs);
        b.putString(ARG_ROOT_ID, rootIdHex);
        f.setArguments(b);
        return f;
    }

    private AppDatabase db;

    public ScheduleMonthFragment() {
        super(R.layout.fragment_schedule_month);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        db = AppDatabase.get(requireContext());
        RecyclerView rv = view.findViewById(R.id.rvMonth);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        long utc = requireArguments().getLong(ARG_MONTH_UTC);
        Calendar anchor = Calendar.getInstance();
        anchor.setTimeInMillis(utc);

        int targetMonth = anchor.get(Calendar.MONTH);
        int targetYear = anchor.get(Calendar.YEAR);

        Calendar first = (Calendar) anchor.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        zeroTime(first);

        Calendar last = (Calendar) first.clone();
        last.add(Calendar.MONTH, 1);
        last.add(Calendar.DATE, -1);
        zeroTime(last);

        Calendar weekStart = startOfWeek(first);

        new Thread(() -> {
            byte[] rootId = MessageCodec.hexToBytes(requireArguments().getString(ARG_ROOT_ID));

            List<TaskWithSource> tasksAll = TaskCollector.collect(db, rootId);

            // filtros mensuales
            List<TaskWithSource> tasks = new ArrayList<>();
            for (TaskWithSource tws : tasksAll) if (ScheduleFilters.showInMonth(tws.task)) tasks.add(tws);

            HashMap<String, Long> startMap = new HashMap<>();
            for (TaskWithSource tws : tasks) {
                TaskEntity t = tws.task;
                TaskChangeEntity create = db.taskChangeDao().findCreateTask(t.taskId);
                long startUtc = (create != null && create.whenApplyUtcMs != null)
                        ? create.whenApplyUtcMs
                        : (create != null ? create.createAtUtcMs : System.currentTimeMillis());
                startMap.put(hex(t.taskId), startUtc);
            }

            List<MonthBlockAdapter.WeekBlock> blocks = new ArrayList<>();
            DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault());

            Calendar ws = (Calendar) weekStart.clone();
            while (!ws.after(last)) {
                Calendar we = (Calendar) ws.clone();
                we.add(Calendar.DATE, 6);

                List<TaskLineAdapter.Line> lines = new ArrayList<>();

                for (int i = 0; i < 7; i++) {
                    Calendar day = (Calendar) ws.clone();
                    day.add(Calendar.DATE, i);

                    if (day.get(Calendar.YEAR) != targetYear || day.get(Calendar.MONTH) != targetMonth) continue;

                    for (TaskWithSource tws : tasks) {
                        TaskEntity t = tws.task;
                        long sUtc = startMap.get(hex(t.taskId));
                        if (!OccurrenceEngine.isActiveOnDay(t, sUtc, day)) continue;

                        boolean effMuted = OverlayResolver.effectiveMuted(db, tws.sourceId, t.taskId, t.muted);

                        String line = getString(R.string.schedule_bullet_task_with_date, df.format(day.getTime()), t.taskName);
                        if (t.startTimeMin != null) line += " " + getString(R.string.schedule_time_parens, minutesToText(t.startTimeMin));
                        lines.add(new TaskLineAdapter.Line(line, effMuted));
                    }
                }

                String header = getString(R.string.week_range, df.format(ws.getTime()), df.format(we.getTime()));
                blocks.add(new MonthBlockAdapter.WeekBlock(header, (Calendar) ws.clone(), lines));

                ws.add(Calendar.DATE, 7);
            }

            requireActivity().runOnUiThread(() -> {
                ScheduleNavigator nav = (ScheduleNavigator) requireActivity();
                rv.setAdapter(new MonthBlockAdapter(blocks, nav));
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