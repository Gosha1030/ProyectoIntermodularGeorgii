package georgii.sytnik.thothtasks.ui;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.Calendar;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.domain.TaskChangeApplier;
import georgii.sytnik.thothtasks.domain.action.ActionChangeTypes;
import georgii.sytnik.thothtasks.domain.action.ActionJson;
import georgii.sytnik.thothtasks.domain.action.ActionKeys;
import georgii.sytnik.thothtasks.domain.action.ActionPlanner;
import georgii.sytnik.thothtasks.domain.validation.TaskHierarchyValidator;
import georgii.sytnik.thothtasks.security.ActionPlanHorizon;
import georgii.sytnik.thothtasks.security.ActionSettingsReader;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;
import georgii.sytnik.thothtasks.util.UuidBytes;

public class EditTaskActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID = "extra_task_id";
    private static final int REQ_PICK_FATHER = 2001;

    private AppDatabase db;

    private TextInputEditText etName, etStart, etFinish, etTimeM, etWeight, etPeriodD;
    private CheckBox cbUninterrupted, cbState, cbMuted;
    private Spinner spType;
    private MaterialButton btnSave, btnDelete, btnPickFather;
    private TextView tvFather;

    // Action switches
    private SwitchCompat swAlarm, swDnd, swNotifyMonth, swNotifyWeek, swNotifyDay, swNotifyOnDay, swNotify1h, swNotify10m, swNotify1m;
    private boolean actionUiReady = false;

    private byte[] taskId;
    private TaskEntity original;

    // Father selection
    private byte[] selectedFatherId; // null => use userRoot
    private String selectedFatherName;

    private byte[] userRootId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_task);

        db = AppDatabase.get(this);

        etName = findViewById(R.id.etName);
        etStart = findViewById(R.id.etStart);
        etFinish = findViewById(R.id.etFinish);
        etTimeM = findViewById(R.id.etTimeM);
        etWeight = findViewById(R.id.etWeight);
        etPeriodD = findViewById(R.id.etPeriodD);

        cbUninterrupted = findViewById(R.id.cbUninterrupted);
        cbState = findViewById(R.id.cbState);
        cbMuted = findViewById(R.id.cbMuted);

        spType = findViewById(R.id.spType);

        btnPickFather = findViewById(R.id.btnPickFather);
        tvFather = findViewById(R.id.tvFather);

        btnDelete = findViewById(R.id.btnDelete);
        btnSave = findViewById(R.id.btnSave);

        // Action switches (must exist in XML)
        swAlarm = findViewById(R.id.swAlarm);
        swDnd = findViewById(R.id.swDnd);
        swNotifyMonth = findViewById(R.id.swNotifyMonth);
        swNotifyWeek = findViewById(R.id.swNotifyWeek);
        swNotifyDay = findViewById(R.id.swNotifyDay);
        swNotifyOnDay = findViewById(R.id.swNotifyOnDay);
        swNotify1h = findViewById(R.id.swNotify1h);
        swNotify10m = findViewById(R.id.swNotify10m);
        swNotify1m = findViewById(R.id.swNotify1m);

        String[] types = new String[]{"Unique", "Daily", "Weekly", "Yearly", "Periodic", "Empty"};
        spType.setAdapter(new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, types));

        taskId = getIntent().getByteArrayExtra(EXTRA_TASK_ID);

        etStart.setOnClickListener(v -> showTimePicker(etStart));
        etFinish.setOnClickListener(v -> showTimePicker(etFinish));

        btnPickFather.setOnClickListener(v -> openFatherPicker());

        btnDelete.setOnClickListener(v -> deleteTask());
        btnSave.setOnClickListener(v -> save());

        loadUserRootAndTask();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // apply scheduled changes (state, mute, and later actions if you add them)
        new Thread(() -> TaskChangeApplier.applyDueStateChanges(db, System.currentTimeMillis())).start();
    }

    private void loadUserRootAndTask() {
        new Thread(() -> {
            byte[] userId = SessionStore.loadLastUserId(this);
            if (userId == null) return;
            UserEntity u = db.userDao().findById(userId);
            if (u == null) return;
            userRootId = u.taskRoot;

            original = db.taskDao().findById(taskId);
            if (original == null) return;

            // Do not allow editing root
            if (equalBytes(original.taskId, userRootId)) {
                runOnUiThread(this::finish);
                return;
            }

            // Ensure actionJson non-null
            if (original.actionJson == null || original.actionJson.trim().isEmpty()) {
                original.actionJson = "{}";
                db.taskDao().setActionJson(original.taskId, "{}");
            }

            selectedFatherId = original.taskFather;
            selectedFatherName = null;

            String fatherLabel = getFatherLabel(original.taskFather, userRootId);
            runOnUiThread(() -> {
                etName.setText(original.taskName);
                spType.setSelection(indexOfType(original.type));
                etStart.setText(minutesToText(original.startTimeMin));
                etFinish.setText(minutesToText(original.finishTimeMin));
                etTimeM.setText(original.timeM == null ? "" : String.valueOf(original.timeM));
                etWeight.setText(original.weight == null ? "" : String.valueOf(original.weight));
                etPeriodD.setText(original.periodD == null ? "" : String.valueOf(original.periodD));
                cbUninterrupted.setChecked(original.uninterrupted);
                cbState.setChecked(original.state);
                cbMuted.setChecked(original.muted);
                tvFather.setText(fatherLabel);

                // ✅ bind action switches now (and only once)
                bindActionSwitches(original);
                actionUiReady = true;
            });
        }).start();
    }

    private void openFatherPicker() {
        Intent i = new Intent(this, TaskPickerActivity.class);
        i.putExtra(TaskPickerActivity.EXTRA_EXCLUDE_ROOT_ID, original != null ? original.taskId : null);
        startActivityForResult(i, REQ_PICK_FATHER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_FATHER && resultCode == RESULT_OK && data != null) {
            selectedFatherId = data.getByteArrayExtra(TaskPickerActivity.RESULT_TASK_ID);
            selectedFatherName = data.getStringExtra(TaskPickerActivity.RESULT_TASK_NAME);
            if (selectedFatherName != null) tvFather.setText(selectedFatherName);
        }
    }

    private void save() {
        if (original == null) return;

        String name = textOf(etName);
        if (name.isEmpty()) name = original.taskName;

        String type = (String) spType.getSelectedItem();

        Integer startMin = parseTimeToMinutes(textOf(etStart));
        Integer finishMin = parseTimeToMinutes(textOf(etFinish));

        if (startMin != null && finishMin != null && startMin >= finishMin) {
            Toast.makeText(this, R.string.toast_invalid_time_order, Toast.LENGTH_SHORT).show();
            return;
        }

        Integer timeM = parseInt(textOf(etTimeM));
        Integer weight = parseInt(textOf(etWeight));
        Integer periodD = parseInt(textOf(etPeriodD));

        boolean uninterrupted = cbUninterrupted.isChecked();
        boolean newState = cbState.isChecked();
        boolean newMuted = cbMuted.isChecked();

        if (!newState) newMuted = false;

        byte[] newFatherId = (selectedFatherId != null) ? selectedFatherId : userRootId;

        boolean changedFather = !equalBytes(orRoot(original.taskFather), newFatherId);
        boolean changedState = newState != original.state;
        boolean changedMuted = newMuted != original.muted;

        boolean changedOther =
                !safeEquals(name, original.taskName)
                        || !safeEquals(type, original.type)
                        || !safeEquals(startMin, original.startTimeMin)
                        || !safeEquals(finishMin, original.finishTimeMin)
                        || !safeEquals(timeM, original.timeM)
                        || !safeEquals(weight, original.weight)
                        || !safeEquals(periodD, original.periodD)
                        || (uninterrupted != original.uninterrupted)
                        || changedFather;

        final boolean finalNewMuted = newMuted;
        final boolean finalNewState = newState;
        final String finalName = name;

        long newStartUtc = System.currentTimeMillis();

        new Thread(() -> {
            long now = System.currentTimeMillis();

            // 1) Only mute change
            if (!changedOther && !changedState && changedMuted) {
                db.taskDao().setMuted(original.taskId, finalNewMuted);

                TaskChangeEntity ch = new TaskChangeEntity();
                ch.taskChangeId = UuidBytes.uuidToBytes(UuidV7.newUuid());
                ch.taskId = original.taskId;
                ch.newTaskId = null;
                ch.type = finalNewMuted ? "mute_on" : "mute_off";
                ch.createAtUtcMs = now;
                ch.whenApplyUtcMs = null;
                db.taskChangeDao().insert(ch);

                runOnUiThread(this::finish);
                return;
            }

            // 2) Only state change
            if (!changedOther && changedState && !changedMuted) {
                db.taskDao().setStateMuted(original.taskId, finalNewState, finalNewMuted);

                TaskChangeEntity ch = new TaskChangeEntity();
                ch.taskChangeId = UuidBytes.uuidToBytes(UuidV7.newUuid());
                ch.taskId = original.taskId;
                ch.newTaskId = null;
                ch.type = finalNewState ? "activate" : "task_deactivate";
                ch.createAtUtcMs = now;
                ch.whenApplyUtcMs = null;
                db.taskChangeDao().insert(ch);

                runOnUiThread(this::finish);
                return;
            }

            // 3) Version update for other changes
            TaskEntity newer = copyOf(original);
            newer.taskId = UuidBytes.uuidToBytes(UuidV7.newUuid());
            newer.taskName = finalName;
            newer.type = type;
            newer.startTimeMin = startMin;
            newer.finishTimeMin = finishMin;
            newer.timeM = timeM;
            newer.weight = weight;
            newer.periodD = periodD;
            newer.uninterrupted = uninterrupted;
            newer.state = finalNewState;
            newer.muted = finalNewMuted;
            newer.taskFather = newFatherId;

            if (!validateCreateTask(newer.type, newer.periodicJson, newer.periodD)) return;

            TaskHierarchyValidator.ValidationResult vr =
                    TaskHierarchyValidator.canChildExistInsideParent(
                            db,
                            newer,
                            newStartUtc,
                            newFatherId
                    );

            if (!vr.ok) {
                runOnUiThread(() -> Toast.makeText(this, vr.message, Toast.LENGTH_LONG).show());
                return;
            }

            TaskHierarchyValidator.ValidationResult vrKids =
                    TaskHierarchyValidator.canLayerDescendantsStillFitInsideNewParent(
                            db,
                            original.taskId,
                            newer,
                            newStartUtc,
                            null
                    );

            if (!vrKids.ok) {
                runOnUiThread(() -> Toast.makeText(this, vrKids.message, Toast.LENGTH_LONG).show());
                return;
            }

            db.taskDao().insert(newer);
            db.taskDao().setStateMuted(original.taskId, false, true);
            db.taskDao().reparentChildren(original.taskId, newer.taskId);

            TaskChangeEntity ch = new TaskChangeEntity();
            ch.taskChangeId = UuidBytes.uuidToBytes(UuidV7.newUuid());
            ch.taskId = original.taskId;
            ch.newTaskId = newer.taskId;
            ch.type = "task_update";
            ch.createAtUtcMs = now;
            ch.whenApplyUtcMs = null;
            db.taskChangeDao().insert(ch);

            runOnUiThread(this::finish);
        }).start();
    }

    private void deleteTask() {
        if (original == null) return;

        new Thread(() -> {
            long now = System.currentTimeMillis();

            db.taskDao().hideSubtree(original.taskId);

            TaskChangeEntity ch = new TaskChangeEntity();
            ch.taskChangeId = UuidBytes.uuidToBytes(UuidV7.newUuid());
            ch.taskId = original.taskId;
            ch.newTaskId = null;
            ch.type = "delete_task";
            ch.createAtUtcMs = now;
            ch.whenApplyUtcMs = null;
            db.taskChangeDao().insert(ch);

            runOnUiThread(this::finish);
        }).start();
    }

    // ---------------- ACTION SWITCHES ----------------

    private void bindActionSwitches(TaskEntity task) {
        // Avoid triggering listeners during init
        swAlarm.setOnCheckedChangeListener(null);
        swDnd.setOnCheckedChangeListener(null);
        swNotifyMonth.setOnCheckedChangeListener(null);
        swNotifyWeek.setOnCheckedChangeListener(null);
        swNotifyDay.setOnCheckedChangeListener(null);
        swNotifyOnDay.setOnCheckedChangeListener(null);
        swNotify1h.setOnCheckedChangeListener(null);
        swNotify10m.setOnCheckedChangeListener(null);
        swNotify1m.setOnCheckedChangeListener(null);

        swAlarm.setChecked(ActionJson.get(task.actionJson, ActionKeys.ALARM));
        swDnd.setChecked(ActionJson.get(task.actionJson, ActionKeys.DND));
        swNotifyMonth.setChecked(ActionJson.get(task.actionJson, ActionKeys.NOTIFY_MONTH));
        swNotifyWeek.setChecked(ActionJson.get(task.actionJson, ActionKeys.NOTIFY_WEEK));
        swNotifyDay.setChecked(ActionJson.get(task.actionJson, ActionKeys.NOTIFY_DAY));
        swNotifyOnDay.setChecked(ActionJson.get(task.actionJson, ActionKeys.NOTIFY_ON_DAY));
        swNotify1h.setChecked(ActionJson.get(task.actionJson, ActionKeys.NOTIFY_1H));
        swNotify10m.setChecked(ActionJson.get(task.actionJson, ActionKeys.NOTIFY_10M));
        swNotify1m.setChecked(ActionJson.get(task.actionJson, ActionKeys.NOTIFY_1M));

        wireAction(swAlarm, task, ActionKeys.ALARM);
        wireAction(swDnd, task, ActionKeys.DND);
        wireAction(swNotifyMonth, task, ActionKeys.NOTIFY_MONTH);
        wireAction(swNotifyWeek, task, ActionKeys.NOTIFY_WEEK);
        wireAction(swNotifyDay, task, ActionKeys.NOTIFY_DAY);
        wireAction(swNotifyOnDay, task, ActionKeys.NOTIFY_ON_DAY);
        wireAction(swNotify1h, task, ActionKeys.NOTIFY_1H);
        wireAction(swNotify10m, task, ActionKeys.NOTIFY_10M);
        wireAction(swNotify1m, task, ActionKeys.NOTIFY_1M);
    }

    private void wireAction(SwitchCompat sw, TaskEntity task, String key) {
        sw.setOnCheckedChangeListener((btn, checked) -> {
            if (!actionUiReady) return;
            new Thread(() -> {
                String newJson = ActionJson.set(task.actionJson, key, checked);
                db.taskDao().setActionJson(task.taskId, newJson);
                task.actionJson = newJson;

                TaskChangeEntity ch = new TaskChangeEntity();
                ch.taskChangeId = UuidBytes.uuidToBytes(UuidV7.newUuid());
                ch.taskId = task.taskId;
                ch.newTaskId = null;
                ch.type = checked ? ActionChangeTypes.on(key) : ActionChangeTypes.off(key);
                ch.createAtUtcMs = System.currentTimeMillis();
                ch.whenApplyUtcMs = null;
                db.taskChangeDao().insert(ch);

                int horizon = ActionPlanHorizon.getDaysAhead(this, db);
                ActionPlanner.scheduleNextDays(getApplicationContext(), db, horizon);
            }).start();
        });
    }

    // ---------------- helpers ----------------

    private String getFatherLabel(byte[] fatherId, byte[] rootId) {
        if (fatherId == null || equalBytes(fatherId, rootId)) return getString(R.string.no_father_selected);
        TaskEntity f = db.taskDao().findById(fatherId);
        return f != null ? f.taskName : getString(R.string.no_father_selected);
    }

    private byte[] orRoot(byte[] maybeNullFather) {
        return maybeNullFather != null ? maybeNullFather : userRootId;
    }

    private static String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void showTimePicker(TextInputEditText target) {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        int m = c.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String s = String.format("%02d:%02d", hourOfDay, minute);
            target.setText(s);
        }, h, m, true).show();
    }

    private int indexOfType(String t) {
        if (t == null) return 0;
        switch (t) {
            case "Unique": return 0;
            case "Daily": return 1;
            case "Weekly": return 2;
            case "Yearly": return 3;
            case "Periodic": return 4;
            case "Empty": return 5;
            default: return 0;
        }
    }

    private static Integer parseTimeToMinutes(String hhmm) {
        if (hhmm == null || hhmm.isEmpty()) return null;
        try {
            String[] p = hhmm.split(":");
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return h * 60 + m;
        } catch (Exception e) {
            return null;
        }
    }

    private static String minutesToText(Integer min) {
        if (min == null) return "";
        int h = min / 60;
        int m = min % 60;
        return String.format("%02d:%02d", h, m);
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    private static TaskEntity copyOf(TaskEntity t) {
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

    private static boolean safeEquals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    private static boolean equalBytes(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    private boolean validateCreateTask(String type, String periodicJson, Integer periodD) {
        if ("Daily".equals(type) && periodD != null) {
            Toast.makeText(this, R.string.err_periodd_not_for_daily, Toast.LENGTH_LONG).show();
            return false;
        }

        if ("Periodic".equals(type) && periodicJson != null && !periodicJson.isEmpty()) {
            try {
                JSONObject o = new JSONObject(periodicJson);
                String unit = o.optString("unit", "");
                int amount = o.optInt("amount", 1);

                if (amount == 1) {
                    Toast.makeText(this, R.string.err_periodic_amount_1, Toast.LENGTH_LONG).show();
                    return false;
                }
                if ("day".equals(unit) && amount == 7) {
                    Toast.makeText(this, R.string.err_periodic_day_7, Toast.LENGTH_LONG).show();
                    return false;
                }
                if ("month".equals(unit) && amount == 12) {
                    Toast.makeText(this, R.string.err_periodic_month_12, Toast.LENGTH_LONG).show();
                    return false;
                }
            } catch (Exception ignored) {}
        }
        return true;
    }
}