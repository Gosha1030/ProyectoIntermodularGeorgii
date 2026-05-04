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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.UUID;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.domain.validation.TaskHierarchyValidator;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;

public class EditTaskActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID = "extra_task_id";
    private static final int REQ_PICK_FATHER = 2001;

    private AppDatabase db;

    private TextInputEditText etName, etStart, etFinish, etTimeM, etPeriodD;
    private CheckBox cbUninterrupted, cbState, cbMuted;
    private Spinner spType;
    private MaterialButton btnSave, btnDelete, btnPickFather;
    private TextView tvFather;

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
        etPeriodD = findViewById(R.id.etPeriodD);

        cbUninterrupted = findViewById(R.id.cbUninterrupted);
        cbState = findViewById(R.id.cbState);
        cbMuted = findViewById(R.id.cbMuted);

        spType = findViewById(R.id.spType);

        btnPickFather = findViewById(R.id.btnPickFather);
        tvFather = findViewById(R.id.tvFather);

        btnDelete = findViewById(R.id.btnDelete);
        btnSave = findViewById(R.id.btnSave);

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

            // Default father selection = current father (or root if null)
            selectedFatherId = original.taskFather; // may be null (rare), treat as root in save
            selectedFatherName = null;

            String fatherLabel = getFatherLabel(original.taskFather, userRootId);

            String finalFatherLabel = fatherLabel;

            runOnUiThread(() -> {
                etName.setText(original.taskName);
                spType.setSelection(indexOfType(original.type));
                etStart.setText(minutesToText(original.startTimeMin));
                etFinish.setText(minutesToText(original.finishTimeMin));
                etTimeM.setText(original.timeM == null ? "" : String.valueOf(original.timeM));
                etPeriodD.setText(original.periodD == null ? "" : String.valueOf(original.periodD));
                cbUninterrupted.setChecked(original.uninterrupted);
                cbState.setChecked(original.state);
                cbMuted.setChecked(original.muted);

                tvFather.setText(finalFatherLabel);
            });
        }).start();
    }

    private void openFatherPicker() {
        // Exclude this task subtree to avoid cycles
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

        // No crossing midnight
        if (startMin != null && finishMin != null && startMin >= finishMin) {
            Toast.makeText(this, R.string.toast_invalid_time_order, Toast.LENGTH_SHORT).show();
            return;
        }

        Integer timeM = parseInt(textOf(etTimeM));
        Integer periodD = parseInt(textOf(etPeriodD));

        boolean uninterrupted = cbUninterrupted.isChecked();
        boolean newState = cbState.isChecked();
        boolean newMuted = cbMuted.isChecked();

        // Rule: if State=false by user => force Muted=false (deactivate is not hide)
        if (!newState) newMuted = false;

        // Father logic: if null, use userRoot
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
                        || !safeEquals(periodD, original.periodD)
                        || (uninterrupted != original.uninterrupted)
                        || changedFather;

        final boolean finalNewMuted = newMuted;
        final boolean finalNewState = newState;

        long newStartUtc = System.currentTimeMillis();

        String finalName = name;
        new Thread(() -> {
            long now = System.currentTimeMillis();

            // 1) Only mute change (and nothing else): update same task, record TaskChange
            if (!changedOther && !changedState && changedMuted) {
                db.taskDao().setMuted(original.taskId, finalNewMuted);

                TaskChangeEntity ch = new TaskChangeEntity();
                ch.taskChangeId = uuidToBytes(UuidV7.newUuid());
                ch.taskId = original.taskId;
                ch.newTaskId = null;
                ch.type = finalNewMuted ? "mute_on" : "mute_off";
                ch.createAtUtcMs = now;
                ch.whenApplyUtcMs = null;
                db.taskChangeDao().insert(ch);

                runOnUiThread(this::finish);
                return;
            }

            // 2) Only state change (and nothing else): update same task, force muted if needed, record TaskChange
            if (!changedOther && changedState && !changedMuted) {
                db.taskDao().setStateMuted(original.taskId, finalNewState, finalNewMuted);

                TaskChangeEntity ch = new TaskChangeEntity();
                ch.taskChangeId = uuidToBytes(UuidV7.newUuid());
                ch.taskId = original.taskId;
                ch.newTaskId = null;
                ch.type = finalNewState ? "activate" : "task_deactivate";
                ch.createAtUtcMs = now;
                ch.whenApplyUtcMs = null;
                db.taskChangeDao().insert(ch);

                runOnUiThread(this::finish);
                return;
            }

            // 3) Any other change (including father): VERSION UPDATE
            TaskEntity newer = copyOf(original);
            newer.taskId = uuidToBytes(UuidV7.newUuid());
            newer.taskName = finalName;
            newer.type = type;
            newer.startTimeMin = startMin;
            newer.finishTimeMin = finishMin;
            newer.timeM = timeM;
            newer.periodD = periodD;
            newer.uninterrupted = uninterrupted;
            newer.state = finalNewState;
            newer.muted = finalNewMuted;
            newer.taskFather = newFatherId;

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

// Validar hijos del padre editado (por capas, saltando empty transparentes)
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


            // Insert new version
            db.taskDao().insert(newer);

            // Old version becomes hidden by versioning: State=false AND Muted=true
            db.taskDao().setStateMuted(original.taskId, false, true);

            // Move children from old to new
            db.taskDao().reparentChildren(original.taskId, newer.taskId);

            // Record TaskChange old -> new
            TaskChangeEntity ch = new TaskChangeEntity();
            ch.taskChangeId = uuidToBytes(UuidV7.newUuid());
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

            // Hide subtree: State=false, Muted=true
            db.taskDao().hideSubtree(original.taskId);

            TaskChangeEntity ch = new TaskChangeEntity();
            ch.taskChangeId = uuidToBytes(UuidV7.newUuid());
            ch.taskId = original.taskId;
            ch.newTaskId = null;
            ch.type = "delete_task";
            ch.createAtUtcMs = now;
            ch.whenApplyUtcMs = null;
            db.taskChangeDao().insert(ch);

            runOnUiThread(this::finish);
        }).start();
    }

    // --- helpers ---

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

    private static byte[] uuidToBytes(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        return new byte[] {
                (byte)(msb >>> 56), (byte)(msb >>> 48), (byte)(msb >>> 40), (byte)(msb >>> 32),
                (byte)(msb >>> 24), (byte)(msb >>> 16), (byte)(msb >>>  8), (byte)(msb),
                (byte)(lsb >>> 56), (byte)(lsb >>> 48), (byte)(lsb >>> 40), (byte)(lsb >>> 32),
                (byte)(lsb >>> 24), (byte)(lsb >>> 16), (byte)(lsb >>>  8), (byte)(lsb)
        };
    }
}