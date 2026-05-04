package georgii.sytnik.thothtasks.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.domain.TaskChangeApplier;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;
import georgii.sytnik.thothtasks.ui.tree.NodeRow;
import georgii.sytnik.thothtasks.ui.tree.TaskTreeAdapter;

public class TaskManagerActivity extends AppCompatActivity {

    private AppDatabase db;
    private RecyclerView rv;
    private TextInputEditText etSearch;
    private FloatingActionButton fab;
    private CheckBox cbShowInactive, cbShowHidden;

    private final List<NodeRow> rows = new ArrayList<>();
    private TaskTreeAdapter adapter;

    private byte[] currentUserId;
    private byte[] currentRootId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_manager);

        db = AppDatabase.get(this);

        rv = findViewById(R.id.rvTree);
        etSearch = findViewById(R.id.etSearch);
        fab = findViewById(R.id.fabCreate);
        cbShowInactive = findViewById(R.id.cbShowInactive);
        cbShowHidden = findViewById(R.id.cbShowHidden);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TaskTreeAdapter(rows, new TaskTreeAdapter.Listener() {
            @Override public void onToggle(NodeRow row, int position) { toggleRow(row, position); }
            @Override public void onClick(NodeRow row) {
                Intent i = new Intent(TaskManagerActivity.this, EditTaskActivity.class);
                i.putExtra(EditTaskActivity.EXTRA_TASK_ID, row.task.taskId);
                startActivity(i);
            }
            @Override public void onLongPress(NodeRow row, View anchor) { showContextMenu(row, anchor); }
        });
        rv.setAdapter(adapter);

        fab.setOnClickListener(v -> startActivity(new Intent(this, CreateTaskActivity.class)));

        cbShowInactive.setOnCheckedChangeListener((b, checked) -> reloadTree());
        cbShowHidden.setOnCheckedChangeListener((b, checked) -> reloadTree());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { reloadTree(); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Mantener DB sincronizada con cambios de estado programados
        new Thread(() -> {
            TaskChangeApplier.applyDueStateChanges(db, System.currentTimeMillis());
            runOnUiThread(this::reloadTree);
        }).start();
    }

    private void reloadTree() {
        currentUserId = SessionStore.loadLastUserId(this);
        final String q = etSearch.getText() != null ? etSearch.getText().toString().trim() : "";
        final boolean includeInactive = cbShowInactive.isChecked();
        final boolean includeHidden = cbShowHidden.isChecked();

        new Thread(() -> {
            rows.clear();

            if (currentUserId == null) {
                runOnUiThread(adapter::notifyDataSetChanged);
                return;
            }

            UserEntity u = db.userDao().findById(currentUserId);
            if (u == null) {
                runOnUiThread(adapter::notifyDataSetChanged);
                return;
            }
            currentRootId = u.taskRoot;

            if (!q.isEmpty()) {
                List<TaskEntity> found = db.taskDao().searchFilteredByName(q, includeInactive, includeHidden);
                for (TaskEntity t : found) {
                    if (equalBytes(t.taskId, currentRootId)) continue;
                    NodeRow r = new NodeRow(t, 0);
                    r.hasChildren = !db.taskDao().childrenFiltered(t.taskId, includeInactive, includeHidden).isEmpty();
                    rows.add(r);
                }
            } else {
                List<TaskEntity> top = db.taskDao().childrenFiltered(currentRootId, includeInactive, includeHidden);
                for (TaskEntity t : top) {
                    NodeRow r = new NodeRow(t, 0);
                    r.hasChildren = !db.taskDao().childrenFiltered(t.taskId, includeInactive, includeHidden).isEmpty();
                    rows.add(r);
                }
            }

            runOnUiThread(adapter::notifyDataSetChanged);
        }).start();
    }

    private void toggleRow(NodeRow row, int position) {
        final boolean includeInactive = cbShowInactive.isChecked();
        final boolean includeHidden = cbShowHidden.isChecked();

        new Thread(() -> {
            if (row.expanded) {
                int removeFrom = position + 1;
                int removeCount = 0;
                while (removeFrom + removeCount < rows.size() && rows.get(removeFrom + removeCount).level > row.level) {
                    removeCount++;
                }
                row.expanded = false;
                int finalRemoveFrom = removeFrom;
                int finalRemoveCount = removeCount;
                runOnUiThread(() -> {
                    if (finalRemoveCount > 0) {
                        rows.subList(finalRemoveFrom, finalRemoveFrom + finalRemoveCount).clear();
                        adapter.notifyItemRangeRemoved(finalRemoveFrom, finalRemoveCount);
                    }
                    adapter.notifyItemChanged(position);
                });
            } else {
                List<TaskEntity> children = db.taskDao().childrenFiltered(row.task.taskId, includeInactive, includeHidden);
                List<NodeRow> insert = new ArrayList<>();
                for (TaskEntity c : children) {
                    NodeRow nr = new NodeRow(c, row.level + 1);
                    nr.hasChildren = !db.taskDao().childrenFiltered(c.taskId, includeInactive, includeHidden).isEmpty();
                    insert.add(nr);
                }
                row.expanded = true;
                runOnUiThread(() -> {
                    rows.addAll(position + 1, insert);
                    adapter.notifyItemChanged(position);
                    adapter.notifyItemRangeInserted(position + 1, insert.size());
                });
            }
        }).start();
    }

    private void showContextMenu(NodeRow row, View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        MenuInflater inflater = pm.getMenuInflater();
        inflater.inflate(R.menu.menu_task_node, pm.getMenu());

        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_toggle_mute) {
                toggleMuteNow(row.task);
                return true;
            } else if (id == R.id.action_activate_now) {
                setStateNow(row.task, true);
                return true;
            } else if (id == R.id.action_deactivate_now) {
                setStateNow(row.task, false);
                return true;
            } else if (id == R.id.action_schedule_activate) {
                pickWhenApply(utc -> scheduleState(row.task, true, utc));
                return true;
            } else if (id == R.id.action_schedule_deactivate) {
                pickWhenApply(utc -> scheduleState(row.task, false, utc));
                return true;
            }
            return false;
        });

        pm.show();
    }

    // ---------------- MUTE (immediate) ----------------

    private void toggleMuteNow(TaskEntity task) {
        new Thread(() -> {
            boolean newMuted = !task.muted;
            db.taskDao().setMuted(task.taskId, newMuted);

            TaskChangeEntity ch = new TaskChangeEntity();
            ch.taskChangeId = uuidToBytes(UuidV7.newUuid());
            ch.taskId = task.taskId;
            ch.newTaskId = null;
            ch.type = newMuted ? "mute_on" : "mute_off";
            ch.createAtUtcMs = System.currentTimeMillis();
            ch.whenApplyUtcMs = null;
            db.taskChangeDao().insert(ch);

            runOnUiThread(this::reloadTree);
        }).start();
    }

    // ---------------- STATE (immediate) ----------------

    private void setStateNow(TaskEntity task, boolean state) {
        new Thread(() -> {
            long now = System.currentTimeMillis();

            // regla: si desactivas => Muted=false (desactivar != ocultar)
            boolean muted = task.muted;
            if (!state) muted = false;

            db.taskDao().setStateMuted(task.taskId, state, muted);

            TaskChangeEntity ch = new TaskChangeEntity();
            ch.taskChangeId = uuidToBytes(UuidV7.newUuid());
            ch.taskId = task.taskId;
            ch.newTaskId = null;
            ch.type = state ? "activate" : "task_deactivate";
            ch.createAtUtcMs = now;
            ch.whenApplyUtcMs = null;
            db.taskChangeDao().insert(ch);

            runOnUiThread(this::reloadTree);
        }).start();
    }

    // ---------------- STATE (scheduled) ----------------

    private interface LongConsumer { void accept(long utcMs); }

    private void pickWhenApply(LongConsumer cb) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (dp, y, m, d) -> {
            Calendar c2 = Calendar.getInstance();
            c2.set(Calendar.YEAR, y);
            c2.set(Calendar.MONTH, m);
            c2.set(Calendar.DAY_OF_MONTH, d);

            new TimePickerDialog(this, (tp, hh, mm) -> {
                c2.set(Calendar.HOUR_OF_DAY, hh);
                c2.set(Calendar.MINUTE, mm);
                c2.set(Calendar.SECOND, 0);
                c2.set(Calendar.MILLISECOND, 0);
                cb.accept(c2.getTimeInMillis());
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();

        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void scheduleState(TaskEntity task, boolean state, long whenApplyUtcMs) {
        new Thread(() -> {
            long now = System.currentTimeMillis();

            TaskChangeEntity ch = new TaskChangeEntity();
            ch.taskChangeId = uuidToBytes(UuidV7.newUuid());
            ch.taskId = task.taskId;
            ch.newTaskId = null;
            ch.type = state ? "activate" : "task_deactivate";
            ch.createAtUtcMs = now;
            ch.whenApplyUtcMs = whenApplyUtcMs;
            db.taskChangeDao().insert(ch);

            runOnUiThread(() -> Toast.makeText(this, "Scheduled", Toast.LENGTH_SHORT).show());
        }).start();
    }

    // ---------------- helpers ----------------

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
