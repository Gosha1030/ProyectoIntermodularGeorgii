package georgii.sytnik.thothtasks;

import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.json.JSONObject;

import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.domain.TaskChangeApplier;
import georgii.sytnik.thothtasks.domain.action.ActionPlanner;
import georgii.sytnik.thothtasks.security.ActionPlanHorizon;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.security.SessionSecrets;
import georgii.sytnik.thothtasks.ui.ExternalUserManagerActivity;
import georgii.sytnik.thothtasks.ui.LoginActivity;
import georgii.sytnik.thothtasks.ui.PlacesTravelsActivity;
import georgii.sytnik.thothtasks.ui.SettingsActivity;
import georgii.sytnik.thothtasks.ui.TaskManagerActivity;
import georgii.sytnik.thothtasks.ui.UserManagerActivity;
import georgii.sytnik.thothtasks.ui.schedule.ScheduleDayFragment;
import georgii.sytnik.thothtasks.ui.schedule.ScheduleMonthFragment;
import georgii.sytnik.thothtasks.ui.schedule.ScheduleNavigator;
import georgii.sytnik.thothtasks.ui.schedule.ScheduleWeekFragment;
import georgii.sytnik.thothtasks.ui.schedule.ScheduleYearFragment;
import georgii.sytnik.thothtasks.util.HexBytes;

public class MainActivity extends AppCompatActivity implements ScheduleNavigator {

    private enum Mode { DAY, WEEK, MONTH, YEAR }

    private AppDatabase db;

    private DrawerLayout drawer;
    private NavigationView navView;
    private MaterialToolbar toolbar;
    private TextView tvRange;

    private Mode mode = Mode.DAY;
    private final Calendar anchor = Calendar.getInstance();

    private GestureDetector gestures;

    private byte[] userId;
    private byte[] rootId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Notification permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 9001);
            }
        }

        ensureActionsChannel();

        db = AppDatabase.get(this);

        drawer = findViewById(R.id.drawer);
        navView = findViewById(R.id.navView);
        toolbar = findViewById(R.id.toolbar);
        tvRange = findViewById(R.id.tvRange);

        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar,
                android.R.string.ok, android.R.string.cancel
        );
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navView.setNavigationItemSelectedListener(this::onNavItemSelected);

        gestures = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 120;
            private static final int SWIPE_VELOCITY = 120;

            @Override public boolean onDown(MotionEvent e) { return false; }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;

                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();

                if (Math.abs(dx) > Math.abs(dy)) {
                    if (Math.abs(dx) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY) {
                        if (dx > 0) shift(-1); else shift(+1);
                        return true;
                    }
                } else {
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
        if (gestures != null) gestures.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Owner UDP listener
        startService(new Intent(this, georgii.sytnik.thothtasks.net.UdpOwnerService.class));

        // Optional: update check worker if you still use it
        georgii.sytnik.thothtasks.ui.work.WorkScheduler.ensureUpdateCheckScheduled(this);

        new Thread(() -> {
            int horizon = ActionPlanHorizon.getDaysAhead(this, db);
            ActionPlanner.scheduleNextDays(getApplicationContext(), db, horizon);
        }).start();


        new Thread(() -> {
            TaskChangeApplier.applyDueStateChanges(db, System.currentTimeMillis());

            userId = SessionStore.loadLastUserId(this);
            if (userId == null) return;
            UserEntity u = db.userDao().findById(userId);
            if (u == null) return;
            rootId = u.taskRoot;

            runOnUiThread(this::render);
        }).start();
    }

    private boolean onNavItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_tasks) startActivity(new Intent(this, TaskManagerActivity.class));
        else if (id == R.id.nav_user_manager) startActivity(new Intent(this, UserManagerActivity.class));
        else if (id == R.id.nav_external_users) startActivity(new Intent(this, ExternalUserManagerActivity.class));
        else if (id == R.id.nav_places_travels) startActivity(new Intent(this, PlacesTravelsActivity.class));
        else if (id == R.id.nav_settings) startActivity(new Intent(this, SettingsActivity.class));
        else if (id == R.id.nav_logout) performLogout();

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void changeMode(int delta) {
        int idx = mode.ordinal() + delta;
        if (idx < 0) idx = 0;
        if (idx > Mode.YEAR.ordinal()) idx = Mode.YEAR.ordinal();
        mode = Mode.values()[idx];
        render();
    }

    private void shift(int delta) {
        switch (mode) {
            case DAY:   anchor.add(Calendar.DATE, delta); break;
            case WEEK:  anchor.add(Calendar.DATE, 7 * delta); break;
            case MONTH: anchor.add(Calendar.MONTH, delta); break;
            case YEAR:  anchor.add(Calendar.YEAR, delta); break;
        }
        render();
    }

    private void render() {
        if (rootId == null) return;

        updateTitles();

        String rootHex = HexBytes.hex(rootId);
        long utc = anchor.getTimeInMillis();

        switch (mode) {
            case DAY:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.scheduleContainer, ScheduleDayFragment.newInstance(utc, rootHex))
                        .commit();
                break;
            case WEEK:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.scheduleContainer, ScheduleWeekFragment.newInstance(utc, rootHex))
                        .commit();
                break;
            case MONTH:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.scheduleContainer, ScheduleMonthFragment.newInstance(utc, rootHex))
                        .commit();
                break;
            case YEAR:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.scheduleContainer, ScheduleYearFragment.newInstance(utc, rootHex))
                        .commit();
                break;
        }
    }

    private void updateTitles() {
        SimpleDateFormat dfDay = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat dfMonth = new SimpleDateFormat("yyyy-MM");
        SimpleDateFormat dfYear = new SimpleDateFormat("yyyy");

        switch (mode) {
            case DAY:
                toolbar.setTitle(getString(R.string.mode_day));
                tvRange.setText(getString(R.string.title_day, dfDay.format(anchor.getTime())));
                break;
            case WEEK: {
                toolbar.setTitle(getString(R.string.mode_week));
                Calendar start = startOfWeek(anchor);
                Calendar end = (Calendar) start.clone();
                end.add(Calendar.DATE, 6);
                tvRange.setText(getString(R.string.title_week, dfDay.format(start.getTime()), dfDay.format(end.getTime())));
                break;
            }
            case MONTH:
                toolbar.setTitle(getString(R.string.mode_month));
                tvRange.setText(getString(R.string.title_month, dfMonth.format(anchor.getTime())));
                break;
            case YEAR:
                toolbar.setTitle(getString(R.string.mode_year));
                tvRange.setText(getString(R.string.title_year, dfYear.format(anchor.getTime())));
                break;
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

    @Override
    public void navigateToDay(Calendar day) {
        mode = Mode.DAY;
        anchor.setTimeInMillis(day.getTimeInMillis());
        render();
    }

    @Override
    public void navigateToWeek(Calendar anyDayInWeek) {
        mode = Mode.WEEK;
        anchor.setTimeInMillis(anyDayInWeek.getTimeInMillis());
        render();
    }

    @Override
    public void navigateToMonth(Calendar anyDayInMonth) {
        mode = Mode.MONTH;
        anchor.setTimeInMillis(anyDayInMonth.getTimeInMillis());
        render();
    }

    private void ensureActionsChannel() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel ch = new android.app.NotificationChannel(
                    "actions", "Actions", android.app.NotificationManager.IMPORTANCE_HIGH
            );
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }


    private void performLogout() {
        byte[] lastUserId = SessionStore.loadLastUserId(this);

        // Mark explicit logout in user settings (best effort)
        if (lastUserId != null) {
            new Thread(() -> {
                try {
                    UserEntity u = db.userDao().findById(lastUserId);
                    if (u != null) {
                        JSONObject obj = u.ajustesJson != null ? new JSONObject(u.ajustesJson) : new JSONObject();
                        obj.put("explicitLogout", true);
                        obj.put("explicitLogoutUtcMs", System.currentTimeMillis());
                        u.ajustesJson = obj.toString();
                        db.userDao().update(u);
                    }
                } catch (Exception ignored) {
                }
            }).start();
        }

        // Clear local session + secrets
        try {
            SessionSecrets.clear();
        } catch (Exception ignored) {
        }
        SessionStore.clear(this);

        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}