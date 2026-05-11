package georgii.sytnik.thothtasks.ui.schedule;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
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
import georgii.sytnik.thothtasks.domain.schedule.TaskCollector;      // <- tu collector que devuelve TaskWithSource
import georgii.sytnik.thothtasks.domain.schedule.TaskWithSource;    // <- wrapper
import georgii.sytnik.thothtasks.net.MessageCodec;

public class ScheduleYearFragment extends Fragment {

    private static final String ARG_YEAR_UTC = "yearUtc";
    private static final String ARG_ROOT_ID = "rootIdHex";

    public static ScheduleYearFragment newInstance(long anyDayUtcMs, String rootIdHex) {
        ScheduleYearFragment f = new ScheduleYearFragment();
        Bundle b = new Bundle();
        b.putLong(ARG_YEAR_UTC, anyDayUtcMs);
        b.putString(ARG_ROOT_ID, rootIdHex);
        f.setArguments(b);
        return f;
    }

    private AppDatabase db;

    public ScheduleYearFragment() {
        super(R.layout.fragment_schedule_year);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        db = AppDatabase.get(requireContext());
        RecyclerView rv = view.findViewById(R.id.rvYear);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        long utc = requireArguments().getLong(ARG_YEAR_UTC);
        Calendar anchor = Calendar.getInstance();
        anchor.setTimeInMillis(utc);
        int targetYear = anchor.get(Calendar.YEAR);

        new Thread(() -> {
            byte[] rootId = MessageCodec.hexToBytes(requireArguments().getString(ARG_ROOT_ID));

            List<TaskWithSource> tasksAll = TaskCollector.collect(db, rootId);

            List<TaskWithSource> tasks = new ArrayList<>();
            for (TaskWithSource tws : tasksAll) {
                if (ScheduleFilters.showInYear(tws.task)) {
                    tasks.add(tws);
                }
            }

            HashMap<String, Long> startMap = new HashMap<>();
            for (TaskWithSource tws : tasks) {
                TaskEntity t = tws.task;
                TaskChangeEntity create = db.taskChangeDao().findCreateTask(t.taskId);
                long startUtc = (create != null && create.whenApplyUtcMs != null)
                        ? create.whenApplyUtcMs
                        : (create != null ? create.createAtUtcMs : System.currentTimeMillis());
                startMap.put(hex(t.taskId), startUtc);
            }

            List<YearBlockAdapter.MonthBlock> blocks = new ArrayList<>();
            SimpleDateFormat dfDay = new SimpleDateFormat("yyyy-MM-dd");
            String[] monthNames = new DateFormatSymbols().getMonths();

            for (int m = 0; m < 12; m++) {
                Calendar first = Calendar.getInstance();
                first.clear();
                first.set(Calendar.YEAR, targetYear);
                first.set(Calendar.MONTH, m);
                first.set(Calendar.DAY_OF_MONTH, 1);

                Calendar last = (Calendar) first.clone();
                last.add(Calendar.MONTH, 1);
                last.add(Calendar.DATE, -1);

                List<TaskLineAdapter.Line> lines = new ArrayList<>();

                Calendar cur = (Calendar) first.clone();
                while (!cur.after(last)) {

                    for (TaskWithSource tws : tasks) {
                        TaskEntity t = tws.task;
                        long sUtc = startMap.get(hex(t.taskId));
                        if (!OccurrenceEngine.isActiveOnDay(t, sUtc, cur)) continue;

                        boolean effMuted = OverlayResolver.effectiveMuted(db, tws.sourceId, t.taskId, t.muted);

                        String line = "• " + dfDay.format(cur.getTime()) + "  " + t.taskName;
                        if (t.startTimeMin != null) line += " (" + minutesToText(t.startTimeMin) + ")";
                        lines.add(new TaskLineAdapter.Line(line, effMuted));
                    }

                    cur.add(Calendar.DATE, 1);
                }

                String header = monthNames[m] + " " + targetYear;
                blocks.add(new YearBlockAdapter.MonthBlock(header, (Calendar) first.clone(), lines));
            }

            requireActivity().runOnUiThread(() -> {
                ScheduleNavigator nav = (ScheduleNavigator) requireActivity();
                rv.setAdapter(new YearBlockAdapter(blocks, nav));
            });
        }).start();
    }

    private static String minutesToText(int min) {
        int h = min / 60;
        int m = min % 60;
        return String.format("%02d:%02d", h, m);
    }

    private static String hex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
