package georgii.sytnik.thothtasks.ui;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
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
import georgii.sytnik.thothtasks.db.entities.PlaceEntity;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.domain.action.ActionChangeTypes;
import georgii.sytnik.thothtasks.domain.action.ActionJson;
import georgii.sytnik.thothtasks.domain.action.ActionKeys;
import georgii.sytnik.thothtasks.domain.action.ActionPlanner;
import georgii.sytnik.thothtasks.domain.validation.TaskHierarchyValidator;
import georgii.sytnik.thothtasks.security.ActionPlanHorizon;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;
import georgii.sytnik.thothtasks.util.TaskTypeUi;
import georgii.sytnik.thothtasks.util.TimeText;
import georgii.sytnik.thothtasks.util.UuidBytes;

public class EditTaskActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID = "extra_task_id";
    private static final int REQ_PICK_FATHER = 2001;

    private AppDatabase db;

    private TextInputEditText etName, etStart, etFinish, etTimeM, etWeight, etPeriodD;
    private CheckBox cbUninterrupted, cbState, cbMuted;
    private Spinner spType;
    private MaterialButton btnSave, btnDelete, btnPickFather, btnPickPlace;
    private TextView tvFather, tvPlace;

    private SwitchCompat swAlarm, swDnd, swNotifyMonth, swNotifyWeek, swNotifyDay, swNotifyOnDay, swNotify1h, swNotify10m, swNotify1m;

    private byte[] taskId;
    private TaskEntity original;

    private byte[] selectedFatherId;
    private byte[] userRootId;

    private byte[] selectedPlaceId = null;

    private volatile boolean actionUiReady = false;

    private final androidx.activity.result.ActivityResultLauncher<Intent> pickPlaceLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == RESULT_OK && res.getData() != null) {
                    selectedPlaceId = res.getData().getByteArrayExtra(PlacePickerActivity.EXTRA_RESULT_PLACE_ID);
                    String name = res.getData().getStringExtra(PlacePickerActivity.EXTRA_RESULT_PLACE_NAME);
                    if (name == null) name = getString(R.string.place_anywhere);
                    tvPlace.setText(name);
                }
            });

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

        btnPickPlace = findViewById(R.id.btnPickPlace);
        tvPlace = findViewById(R.id.tvPlace);

        btnDelete = findViewById(R.id.btnDelete);
        btnSave = findViewById(R.id.btnSave);

        swAlarm = findViewById(R.id.swAlarm);
        swDnd = findViewById(R.id.swDnd);
        swNotifyMonth = findViewById(R.id.swNotifyMonth);
        swNotifyWeek = findViewById(R.id.swNotifyWeek);
        swNotifyDay = findViewById(R.id.swNotifyDay);
        swNotifyOnDay = findViewById(R.id.swNotifyOnDay);
        swNotify1h = findViewById(R.id.swNotify1h);
        swNotify10m = findViewById(R.id.swNotify10m);
        swNotify1m = findViewById(R.id.swNotify1m);

        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.task_type_labels,
                android.R.layout.simple_spinner_dropdown_item
        );
        spType.setAdapter(typeAdapter);

        taskId = getIntent().getByteArrayExtra(EXTRA_TASK_ID);

        etStart.setOnClickListener(v -> showTimePicker(etStart));
        etFinish.setOnClickListener(v -> showTimePicker(etFinish));

        btnPickFather.setOnClickListener(v -> openFatherPicker());

        btnPickPlace.setOnClickListener(v -> {
            Intent i = new Intent(this, PlacePickerActivity.class);
            i.putExtra(PlacePickerActivity.EXTRA_ALLOW_ANY, true);
            pickPlaceLauncher.launch(i);
        });

        btnDelete.setOnClickListener(v -> deleteTask());
        btnSave.setOnClickListener(v -> save());

        // Live enable/disable for timed actions (Option 1)
        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateTimedActionsAvailabilityFromInputs();
            }
        };
        etStart.addTextChangedListener(w);
        etFinish.addTextChangedListener(w);
        etTimeM.addTextChangedListener(w);

        loadUserRootAndTask();
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

            // Ensure actionJson not null
            if (original.actionJson == null || original.actionJson.trim().isEmpty()) {
                original.actionJson = "{}";
                db.taskDao().setActionJson(original.taskId, "{}");
            }

            selectedFatherId = original.taskFather;
            selectedPlaceId = original.placeId;

            String fatherLabel = getFatherLabel(original.taskFather, userRootId);

            String placeLabel = resolvePlaceName(original.placeId);

            runOnUiThread(() -> {
                etName.setText(original.taskName);
                spType.setSelection(TaskTypeUi.indexOfValue(this, original.type));
                etStart.setText(TimeText.minutesToText(original.startTimeMin));
                etFinish.setText(TimeText.minutesToText(original.finishTimeMin));
                etTimeM.setText(original.timeM == null ? "" : String.valueOf(original.timeM));
                etWeight.setText(original.weight == null ? "" : String.valueOf(original.weight));
                etPeriodD.setText(original.periodD == null ? "" : String.valueOf(original.periodD));

                cbUninterrupted.setChecked(original.uninterrupted);
                cbState.setChecked(original.state);
                cbMuted.setChecked(original.muted);

                tvFather.setText(fatherLabel);
                tvPlace.setText(placeLabel);

                bindActionSwitches(original);
                updateTimedActionsAvailability(original);

                actionUiReady = true;
            });
        }).start();
    }

    private String resolvePlaceName(byte[] placeId) {
        if (placeId == null) return getString(R.string.place_anywhere);
        PlaceEntity p = db.placeDao().findById(placeId);
        return (p != null) ? p.placeName : getString(R.string.place_anywhere);
    }

    private void bindActionSwitches(TaskEntity task) {
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

            // Option 1: if timed actions currently disabled, user cannot toggle (switch disabled).
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

                ActionPlanner.scheduleNextDays(getApplicationContext(), db, ActionPlanHorizon.getDaysAhead(this, db));
            }).start();
        });
    }

    private void updateTimedActionsAvailabilityFromInputs() {
        Integer startMin = TimeText.parseTimeToMinutes(textOf(etStart));
        Integer finishMin = TimeText.parseTimeToMinutes(textOf(etFinish));
        Integer timeM = parseIntOrNull(textOf(etTimeM));
        setTimedActionSwitchesEnabled(canTimedActions(timeM, startMin, finishMin));
    }

    private void updateTimedActionsAvailability(TaskEntity t) {
        setTimedActionSwitchesEnabled(canTimedActions(t.timeM, t.startTimeMin, t.finishTimeMin));
    }

    private void setTimedActionSwitchesEnabled(boolean enabled) {
        swAlarm.setEnabled(enabled);
        swDnd.setEnabled(enabled);
        swNotify1h.setEnabled(enabled);
        swNotify10m.setEnabled(enabled);
        swNotify1m.setEnabled(enabled);
    }

    private boolean canTimedActions(Integer timeM, Integer startMin, Integer finishMin) {
        return (timeM != null) || (startMin != null && finishMin != null);
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
            String fatherName = data.getStringExtra(TaskPickerActivity.RESULT_TASK_NAME);
            if (fatherName != null) tvFather.setText(fatherName);
        }
    }

    private void save() {
        if (original == null) return;

        String name = textOf(etName);
        if (name.isEmpty()) name = original.taskName;

        String type = TaskTypeUi.valueAt(this, spType.getSelectedItemPosition());

        Integer startMin = TimeText.parseTimeToMinutes(textOf(etStart));
        Integer finishMin = TimeText.parseTimeToMinutes(textOf(etFinish));

        if (startMin != null && finishMin != null && startMin >= finishMin) {
            Toast.makeText(this, R.string.toast_invalid_time_order, Toast.LENGTH_SHORT).show();
            return;
        }

        Integer timeM = parseIntOrNull(textOf(etTimeM));
        Integer weight = parseIntOrNull(textOf(etWeight));
        Integer periodD = parseIntOrNull(textOf(etPeriodD));

        boolean uninterrupted = cbUninterrupted.isChecked();
        boolean newState = cbState.isChecked();
        boolean newMuted = cbMuted.isChecked();

        if (!newState) newMuted = false;

        byte[] newFatherId = (selectedFatherId != null) ? selectedFatherId : userRootId;
        byte[] newPlaceId = selectedPlaceId;

        boolean changedFather = !equalsBytes(orRoot(original.taskFather), newFatherId);
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
                        || changedFather
                        || !equalsBytes(original.placeId, newPlaceId);

        final boolean finalNewState = newState;
        final boolean finalNewMuted = newMuted;
        final String finalName = name;

        long newStartUtc = System.currentTimeMillis();

        new Thread(() -> {
            long now = System.currentTimeMillis();

            // Only mute change
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

            // Only state change
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

            // Version update for other changes
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
            newer.placeId = newPlaceId;

            if (!validateCreateTask(newer.type, newer.periodicJson, newer.periodD)) return;

            TaskHierarchyValidator.ValidationResult vr =
                    TaskHierarchyValidator.canChildExistInsideParent(db, newer, newStartUtc, newFatherId);

            if (!vr.ok) {
                runOnUiThread(() -> Toast.makeText(this, vr.message, Toast.LENGTH_LONG).show());
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

    private void showTimePicker(TextInputEditText target) {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        int m = c.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String s = String.format("%02d:%02d", hourOfDay, minute);
            target.setText(s);
        }, h, m, true).show();
    }

    private String getFatherLabel(byte[] fatherId, byte[] rootId) {
        if (fatherId == null || equalsBytes(fatherId, rootId)) return getString(R.string.no_father_selected);
        TaskEntity f = db.taskDao().findById(fatherId);
        return f != null ? f.taskName : getString(R.string.no_father_selected);
    }

    private byte[] orRoot(byte[] maybeNullFather) {
        return maybeNullFather != null ? maybeNullFather : userRootId;
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

    private static String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private static Integer parseIntOrNull(String s) {
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

    private static boolean equalsBytes(byte[] a, byte[] b) {
        if (a == null && b == null) return true;
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

    private int indexOfTypeValue(String value) {
        String[] values = getResources().getStringArray(R.array.task_type_values);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return 0;
    }
}