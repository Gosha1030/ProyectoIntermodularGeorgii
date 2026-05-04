package georgii.sytnik.thothtasks;

import android.view.MenuItem;
import android.content.Intent;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import android.os.Bundle;
import android.view.GestureDetector;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.domain.TaskChangeApplier;
import georgii.sytnik.thothtasks.domain.schedule.OccurrenceEngine;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.ui.TaskManagerActivity;
import georgii.sytnik.thothtasks.ui.schedule.ScheduleAdapter;
import georgii.sytnik.thothtasks.ui.schedule.ScheduleTaskRow;

public class MainActivity extends AppCompatActivity {

    private enum Mode { DAY, WEEK, MONTH, YEAR }

    private AppDatabase db;

    private DrawerLayout drawer;
    private NavigationView navView;
    private MaterialToolbar toolbar;

    private TextView tvRange;
    private RecyclerView rv;

    private final List<ScheduleTaskRow> rows = new ArrayList<>();
    private ScheduleAdapter adapter;

    private Mode mode = Mode.DAY;
    private final Calendar anchor = Calendar.getInstance();

    private GestureDetector gestures;

    private byte[] userId;
    private byte[] rootId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = AppDatabase.get(this);

        drawer = findViewById(R.id.drawer);
        navView = findViewById(R.id.navView);
        toolbar = findViewById(R.id.toolbar);
        tvRange = findViewById(R.id.tvRange);
        rv = findViewById(R.id.rvSchedule);

        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar,
                android.R.string.ok, android.R.string.cancel
        );
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navView.setNavigationItemSelectedListener(this::onNavItemSelected);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ScheduleAdapter(rows);
        rv.setAdapter(adapter);

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 120;
            private static final int SWIPE_VELOCITY = 120;

            @Override public boolean onDown(MotionEvent e) { return false; }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();

                // Horizontal
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (Math.abs(dx) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY) {
                        if (dx > 0) shift(-1); else shift(+1);
                        return true;
                    }
                } else {
                    // Vertical
                    if (Math.abs(dy) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY) {
                        if (dy < 0) changeMode(+1); else changeMode(-1);
                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Detect swipes globally without breaking RecyclerView scroll
        if (gestures != null) gestures.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();

        new Thread(() -> {
            // keep DB in sync with scheduled state changes
            TaskChangeApplier.applyDueStateChanges(db, System.currentTimeMillis());

            userId = SessionStore.loadLastUserId(this);
            if (userId == null) return;
            UserEntity u = db.userDao().findById(userId);
            if (u == null) return;
            rootId = u.taskRoot;

            runOnUiThread(this::refresh);
        }).start();
    }

    private boolean onNavItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_tasks) {
            startActivity(new Intent(this, TaskManagerActivity.class));
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void changeMode(int delta) {
        int idx = mode.ordinal() + delta;
        if (idx < 0) idx = 0;
        if (idx > Mode.YEAR.ordinal()) idx = Mode.YEAR.ordinal();
        mode = Mode.values()[idx];
        refresh();
    }

    private void shift(int delta) {
        switch (mode) {
            case DAY:   anchor.add(Calendar.DATE, delta); break;
            case WEEK:  anchor.add(Calendar.DATE, 7 * delta); break;
            case MONTH: anchor.add(Calendar.MONTH, delta); break;
            case YEAR:  anchor.add(Calendar.YEAR, delta); break;
        }
        refresh();
    }

    private void refresh() {
        updateRangeTitle();
        loadScheduleForCurrentMode();
    }

    private void updateRangeTitle() {
        SimpleDateFormat dfDay = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dfMonth = new SimpleDateFormat("yyyy-MM");
        SimpleDateFormat dfYear = new SimpleDateFormat("yyyy");

        switch (mode) {
            case DAY:
                tvRange.setText("Día: " + dfDay.format(anchor.getTime()));
                return;
            case WEEK: {
                Calendar start = startOfWeek(anchor);
                Calendar end = (Calendar) start.clone();
                end.add(Calendar.DATE, 6);
                tvRange.setText("Semana: " + dfDay.format(start.getTime()) + " — " + dfDay.format(end.getTime()));
                return;
            }
            case MONTH:
                tvRange.setText("Mes: " + dfMonth.format(anchor.getTime()));
                return;
            case YEAR:
                tvRange.setText("Año: " + dfYear.format(anchor.getTime()));
        }
    }

    private void loadScheduleForCurrentMode() {
        rows.clear();
        adapter.notifyDataSetChanged();

        if (rootId == null) return;

        new Thread(() -> {
            List<TaskEntity> tasks = new ArrayList<>();
            collectActiveSubtree(rootId, tasks);

            HashMap<String, Long> startMap = new HashMap<>();
            for (TaskEntity t : tasks) {
                TaskChangeEntity create = db.taskChangeDao().findCreateTask(t.taskId);
                long startUtc = (create != null && create.whenApplyUtcMs != null)
                        ? create.whenApplyUtcMs
                        : (create != null ? create.createAtUtcMs : System.currentTimeMillis());
                startMap.put(hex(t.taskId), startUtc);
            }

            List<Calendar> days = enumerateDaysForMode(mode, anchor);

            for (Calendar day : days) {
                for (TaskEntity t : tasks) {
                    long startUtc = startMap.get(hex(t.taskId));
                    if (!OccurrenceEngine.isActiveOnDay(t, startUtc, day)) continue;

                    int sortKey = (t.startTimeMin != null) ? t.startTimeMin : 10_000;
                    String timeText = (t.startTimeMin != null) ? minutesToText(t.startTimeMin) : "--:--";
                    String sub = buildSubText(t);

                    if (mode == Mode.WEEK) {
                        SimpleDateFormat df = new SimpleDateFormat("E dd");
                        sub = df.format(day.getTime()) + " • " + sub;
                        sortKey = day.get(Calendar.DAY_OF_YEAR) * 2000 + ((t.startTimeMin != null) ? t.startTimeMin : 1500);
                    } else if (mode == Mode.MONTH || mode == Mode.YEAR) {
                        // v0: only anchor day to avoid huge lists; next iteration will do grid/summary
                        if (!sameDay(day, anchor)) continue;
                    }

                    rows.add(new ScheduleTaskRow(t, sortKey, timeText, sub));
                }
            }

            Collections.sort(rows, Comparator.comparingInt(r -> r.sortKey));

            runOnUiThread(() -> adapter.notifyDataSetChanged());
        }).start();
    }

    private List<Calendar> enumerateDaysForMode(Mode mode, Calendar anchor) {
        List<Calendar> out = new ArrayList<>();
        if (mode == Mode.DAY) {
            out.add((Calendar) anchor.clone());
        } else if (mode == Mode.WEEK) {
            Calendar s = startOfWeek(anchor);
            for (int i = 0; i < 7; i++) {
                Calendar d = (Calendar) s.clone();
                d.add(Calendar.DATE, i);
                out.add(d);
            }
        } else {
            out.add((Calendar) anchor.clone());
        }
        return out;
    }

    private void collectActiveSubtree(byte[] fatherId, List<TaskEntity> out) {
        List<TaskEntity> children = db.taskDao().childrenActiveOf(fatherId);
        for (TaskEntity c : children) {
            out.add(c);
            collectActiveSubtree(c.taskId, out);
        }
    }

    private static Calendar startOfWeek(Calendar any) {
        Calendar c = (Calendar) any.clone();
        int dow = c.get(Calendar.DAY_OF_WEEK);
        int diff = (dow == Calendar.SUNDAY) ? -6 : (Calendar.MONDAY - dow);
        c.add(Calendar.DATE, diff);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static String buildSubText(TaskEntity t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.type != null ? t.type : "Task");
        if (t.timeM != null) sb.append(" • ").append(t.timeM).append("m");
        if (t.finishTimeMin != null && t.startTimeMin != null) {
            sb.append(" • ").append(minutesToText(t.startTimeMin)).append("-").append(minutesToText(t.finishTimeMin));
        }
        if (t.muted) sb.append(" • muted");
        return sb.toString();
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
