package georgii.sytnik.thothtasks.ui;

import static georgii.sytnik.thothtasks.util.HexBytes.equalBytes;

import android.app.AlertDialog;
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
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.ExternalSourceEntity;
import georgii.sytnik.thothtasks.db.entities.TaskChangeEntity;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.TaskOverlayEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.domain.TaskChangeApplier;
import georgii.sytnik.thothtasks.domain.action.ActionKeys;
import georgii.sytnik.thothtasks.domain.action.ActionPlanner;
import georgii.sytnik.thothtasks.domain.schedule.OverlayResolver;
import georgii.sytnik.thothtasks.security.ActionPlanHorizon;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.time.UuidV7;
import georgii.sytnik.thothtasks.ui.tree.NodeRow;
import georgii.sytnik.thothtasks.ui.tree.TaskTreeAdapter;
import georgii.sytnik.thothtasks.util.HexBytes;
import georgii.sytnik.thothtasks.util.UuidBytes;

public class TaskManagerActivity extends AppCompatActivity {

    private final List<NodeRow> rows = new ArrayList<>();
    private AppDatabase db;
    private RecyclerView rv;
    private TextInputEditText etSearch;
    private FloatingActionButton fab;
    private CheckBox cbShowInactive, cbShowHidden;
    private TaskTreeAdapter adapter;

    private byte[] currentUserId;
    private byte[] currentRootId;
    private NodeRow pendingImportedRowForPlace;
    private final androidx.activity.result.ActivityResultLauncher<Intent> pickPlaceImportedLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), res -> {
        if (pendingImportedRowForPlace == null) return;
        if (res.getResultCode() == RESULT_OK && res.getData() != null) {
            byte[] placeId = res.getData().getByteArrayExtra(PlacePickerActivity.EXTRA_RESULT_PLACE_ID);
            setPlaceLocal(pendingImportedRowForPlace.sourceId, pendingImportedRowForPlace.task.taskId, placeId);
        }
        pendingImportedRowForPlace = null;
    });

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
            @Override
            public void onToggle(NodeRow row, int position) {
                toggleRow(row, position);
            }

            @Override
            public void onClick(NodeRow row) {
                if (row.sourceId == null) {
                    Intent i = new Intent(TaskManagerActivity.this, EditTaskActivity.class);
                    i.putExtra(EditTaskActivity.EXTRA_TASK_ID, row.task.taskId);
                    startActivity(i);
                } else {
                    showImportedOverlayDialog(row);
                }
            }

            @Override
            public void onLongPress(NodeRow row, View anchor) {
                showContextMenu(row, anchor);
            }
        });
        rv.setAdapter(adapter);

        fab.setOnClickListener(v -> startActivity(new Intent(this, CreateTaskActivity.class)));

        cbShowInactive.setOnCheckedChangeListener((b, checked) -> reloadTree());
        cbShowHidden.setOnCheckedChangeListener((b, checked) -> reloadTree());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                reloadTree();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
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
                List<ExternalSourceEntity> allSources = db.externalSourceDao().listAll();
                Map<String, byte[]> importedRootToSource = new HashMap<>();
                for (ExternalSourceEntity s : allSources) {
                    if (s.importedRootTaskId != null)
                        importedRootToSource.put(HexBytes.hex(s.importedRootTaskId), s.sourceId);
                }
                List<TaskEntity> top = db.taskDao().childrenFiltered(currentRootId, includeInactive, includeHidden);
                for (TaskEntity t : top) {
                    NodeRow r = new NodeRow(t, 0);
                    byte[] sourceId = importedRootToSource.get(HexBytes.hex(t.taskId));
                    r.sourceId = sourceId;
                    r.effectiveMuted = OverlayResolver.effectiveMuted(db, r.sourceId, t.taskId, t.muted);
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
                    nr.sourceId = row.sourceId; // inherit
                    nr.effectiveMuted = OverlayResolver.effectiveMuted(db, nr.sourceId, c.taskId, c.muted);
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
        if (row.sourceId != null) {
            showImportedContextMenu(row, anchor);
            return;
        }

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

    private void showImportedContextMenu(NodeRow row, View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        MenuInflater inflater = pm.getMenuInflater();
        inflater.inflate(R.menu.menu_imported_task, pm.getMenu());

        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_mute_local_on) {
                toggleMuteLocal(row.sourceId, row.task, true);
                return true;
            } else if (id == R.id.action_mute_local_off) {
                toggleMuteLocal(row.sourceId, row.task, false);
                return true;
            } else if (id == R.id.action_edit_action_local) {
                showImportedActionDialog(row);
                return true;
            } else if (id == R.id.action_clear_action_local) {
                clearActionLocal(row.sourceId, row.task.taskId);
                return true;
            } else if (id == R.id.action_set_place_local) {
                pickPlaceForImported(row);
                return true;
            } else if (id == R.id.action_clear_place_local) {
                setPlaceLocal(row.sourceId, row.task.taskId, null);
                return true;
            }
            return false;
        });

        pm.show();
    }

    private void toggleMuteNow(TaskEntity task) {
        new Thread(() -> {
            boolean newMuted = !task.muted;
            db.taskDao().setMuted(task.taskId, newMuted);

            TaskChangeEntity ch = new TaskChangeEntity();
            ch.taskChangeId = UuidBytes.uuidToBytes(UuidV7.newUuid());
            ch.taskId = task.taskId;
            ch.newTaskId = null;
            ch.type = newMuted ? "mute_on" : "mute_off";
            ch.createAtUtcMs = System.currentTimeMillis();
            ch.whenApplyUtcMs = null;
            db.taskChangeDao().insert(ch);

            runOnUiThread(this::reloadTree);
        }).start();
    }

    private void toggleMuteLocal(byte[] sourceId, TaskEntity task, boolean currentEffectiveMuted) {
        new Thread(() -> {
            TaskOverlayEntity ov = db.taskOverlayDao().find(sourceId, task.taskId);
            if (ov == null) {
                ov = new TaskOverlayEntity();
                ov.overlayId = UuidBytes.uuidToBytes(UuidV7.newUuid());
                ov.sourceId = sourceId;
                ov.taskId = task.taskId;
            }
            ov.mutedLocal = !currentEffectiveMuted;
            ov.updatedAtUtcMs = System.currentTimeMillis();
            db.taskOverlayDao().upsert(ov);

            runOnUiThread(this::reloadTree);
        }).start();
    }

    private void setStateNow(TaskEntity task, boolean state) {
        new Thread(() -> {
            long now = System.currentTimeMillis();
            boolean muted = task.muted;
            if (!state) muted = false;

            db.taskDao().setStateMuted(task.taskId, state, muted);

            TaskChangeEntity ch = new TaskChangeEntity();
            ch.taskChangeId = UuidBytes.uuidToBytes(UuidV7.newUuid());
            ch.taskId = task.taskId;
            ch.newTaskId = null;
            ch.type = state ? "activate" : "task_deactivate";
            ch.createAtUtcMs = now;
            ch.whenApplyUtcMs = null;
            db.taskChangeDao().insert(ch);

            runOnUiThread(this::reloadTree);
        }).start();
    }

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
            ch.taskChangeId = UuidBytes.uuidToBytes(UuidV7.newUuid());
            ch.taskId = task.taskId;
            ch.newTaskId = null;
            ch.type = state ? "activate" : "task_deactivate";
            ch.createAtUtcMs = now;
            ch.whenApplyUtcMs = whenApplyUtcMs;
            db.taskChangeDao().insert(ch);

            runOnUiThread(() -> Toast.makeText(this, "Scheduled", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void showImportedOverlayDialog(NodeRow row) {
        if (row.sourceId == null) return;

        String[] options = new String[]{"Mute (local)", "Action (local)", "Clear Action (local)"};

        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(row.task.taskName).setItems(options, (dlg, which) -> {
            if (which == 0) {
                toggleMuteLocal(row.sourceId, row.task, row.effectiveMuted);
            } else if (which == 1) {
                showActionLocalDialog(row.sourceId, row.task);
            } else if (which == 2) {
                clearActionLocal(row.sourceId, row.task);
            }
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showActionLocalDialog(byte[] sourceId, TaskEntity task) {
        com.google.android.material.textfield.TextInputEditText et = new com.google.android.material.textfield.TextInputEditText(this);
        et.setHint("{\"type\":\"notify\",\"beforeMin\":60}");

        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Action (local) • " + task.taskName).setView(et).setPositiveButton("Save", (d, w) -> {
            String json = et.getText() != null ? et.getText().toString().trim() : "";
            setActionLocal(sourceId, task, json.isEmpty() ? null : json);
        }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void setActionLocal(byte[] sourceId, TaskEntity task, String actionJsonOrNull) {
        new Thread(() -> {
            TaskOverlayEntity ov = db.taskOverlayDao().find(sourceId, task.taskId);
            if (ov == null) {
                ov = new TaskOverlayEntity();
                ov.overlayId = UuidBytes.uuidToBytes(UuidV7.newUuid());
                ov.sourceId = sourceId;
                ov.taskId = task.taskId;
            }
            ov.actionLocalJson = actionJsonOrNull;
            ov.updatedAtUtcMs = System.currentTimeMillis();
            db.taskOverlayDao().upsert(ov);

            runOnUiThread(this::reloadTree);
        }).start();
    }

    private void clearActionLocal(byte[] sourceId, TaskEntity task) {
        setActionLocal(sourceId, task, null);
    }

    private void pickPlaceForImported(NodeRow row) {
        pendingImportedRowForPlace = row;
        Intent i = new Intent(this, PlacePickerActivity.class);
        i.putExtra(PlacePickerActivity.EXTRA_ALLOW_ANY, true);
        pickPlaceImportedLauncher.launch(i);
    }

    private void setPlaceLocal(byte[] sourceId, byte[] taskId, byte[] placeIdOrNull) {
        new Thread(() -> {
            TaskOverlayEntity ov = db.taskOverlayDao().find(sourceId, taskId);
            if (ov == null) {
                ov = new TaskOverlayEntity();
                ov.overlayId = georgii.sytnik.thothtasks.util.UuidBytes.uuidToBytes(UuidV7.newUuid());
                ov.sourceId = sourceId;
                ov.taskId = taskId;
            }
            ov.placeLocalId = placeIdOrNull;
            ov.updatedAtUtcMs = System.currentTimeMillis();
            db.taskOverlayDao().upsert(ov);

            runOnUiThread(this::reloadTree);
        }).start();
    }

    private void showImportedActionDialog(NodeRow row) {
        if (row.sourceId == null) return;
        new Thread(() -> {
            TaskOverlayEntity ov = db.taskOverlayDao().find(row.sourceId, row.task.taskId);
            String overlayJson = (ov != null && ov.actionLocalJson != null) ? ov.actionLocalJson : null;

            runOnUiThread(() -> {
                View v = getLayoutInflater().inflate(R.layout.dialog_action_local, null);

                SwitchCompat swAlarm = v.findViewById(R.id.swAlarm);
                SwitchCompat swDnd = v.findViewById(R.id.swDnd);
                SwitchCompat swNotifyMonth = v.findViewById(R.id.swNotifyMonth);
                SwitchCompat swNotifyWeek = v.findViewById(R.id.swNotifyWeek);
                SwitchCompat swNotifyDay = v.findViewById(R.id.swNotifyDay);
                SwitchCompat swNotifyOnDay = v.findViewById(R.id.swNotifyOnDay);
                SwitchCompat swNotify1h = v.findViewById(R.id.swNotify1h);
                SwitchCompat swNotify10m = v.findViewById(R.id.swNotify10m);
                SwitchCompat swNotify1m = v.findViewById(R.id.swNotify1m);

                JSONObject o = georgii.sytnik.thothtasks.domain.action.ActionJson.parseOrEmpty(overlayJson);

                swAlarm.setChecked(o.optBoolean(ActionKeys.ALARM, false));
                swDnd.setChecked(o.optBoolean(ActionKeys.DND, false));
                swNotifyMonth.setChecked(o.optBoolean(ActionKeys.NOTIFY_MONTH, false));
                swNotifyWeek.setChecked(o.optBoolean(ActionKeys.NOTIFY_WEEK, false));
                swNotifyDay.setChecked(o.optBoolean(ActionKeys.NOTIFY_DAY, false));
                swNotifyOnDay.setChecked(o.optBoolean(ActionKeys.NOTIFY_ON_DAY, false));
                swNotify1h.setChecked(o.optBoolean(ActionKeys.NOTIFY_1H, false));
                swNotify10m.setChecked(o.optBoolean(ActionKeys.NOTIFY_10M, false));
                swNotify1m.setChecked(o.optBoolean(ActionKeys.NOTIFY_1M, false));

                boolean hasExactTime = (row.task.startTimeMin != null && row.task.finishTimeMin != null);
                swAlarm.setEnabled(hasExactTime);
                swNotify1h.setEnabled(hasExactTime);
                swNotify10m.setEnabled(hasExactTime);
                swNotify1m.setEnabled(hasExactTime);

                AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Action (local) • " + row.task.taskName).setView(v).setPositiveButton("Save", null).setNegativeButton(android.R.string.cancel, null).show();

                dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
                    String json = buildOverlayActionJson(hasExactTime, swAlarm.isChecked(), swDnd.isChecked(), swNotifyMonth.isChecked(), swNotifyWeek.isChecked(), swNotifyDay.isChecked(), swNotifyOnDay.isChecked(), swNotify1h.isChecked(), swNotify10m.isChecked(), swNotify1m.isChecked());

                    saveActionLocal(row.sourceId, row.task.taskId, json);
                    dlg.dismiss();
                });
            });
        }).start();
    }

    private String buildOverlayActionJson(boolean hasExactTime, boolean alarm, boolean dnd, boolean nMonth, boolean nWeek, boolean nDay, boolean nOnDay, boolean n1h, boolean n10m, boolean n1m) {
        try {
            JSONObject o = new JSONObject();

            o.put(ActionKeys.NOTIFY_MONTH, nMonth);
            o.put(ActionKeys.NOTIFY_WEEK, nWeek);
            o.put(ActionKeys.NOTIFY_DAY, nDay);
            o.put(ActionKeys.NOTIFY_ON_DAY, nOnDay);
            o.put(ActionKeys.DND, dnd);
            o.put(ActionKeys.ALARM, hasExactTime && alarm);
            o.put(ActionKeys.NOTIFY_1H, hasExactTime && n1h);
            o.put(ActionKeys.NOTIFY_10M, hasExactTime && n10m);
            o.put(ActionKeys.NOTIFY_1M, hasExactTime && n1m);

            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private void saveActionLocal(byte[] sourceId, byte[] taskId, String json) {
        new Thread(() -> {
            TaskOverlayEntity ov = db.taskOverlayDao().find(sourceId, taskId);
            if (ov == null) {
                ov = new TaskOverlayEntity();
                ov.overlayId = georgii.sytnik.thothtasks.util.UuidBytes.uuidToBytes(UuidV7.newUuid());
                ov.sourceId = sourceId;
                ov.taskId = taskId;
            }
            ov.actionLocalJson = json;
            ov.updatedAtUtcMs = System.currentTimeMillis();
            db.taskOverlayDao().upsert(ov);

            int horizon = ActionPlanHorizon.getDaysAhead(this, db);
            ActionPlanner.scheduleNextDays(getApplicationContext(), db, horizon);

            runOnUiThread(this::reloadTree);
        }).start();
    }

    private void clearActionLocal(byte[] sourceId, byte[] taskId) {
        new Thread(() -> {
            TaskOverlayEntity ov = db.taskOverlayDao().find(sourceId, taskId);
            if (ov != null) {
                ov.actionLocalJson = null;
                ov.updatedAtUtcMs = System.currentTimeMillis();
                db.taskOverlayDao().upsert(ov);
            }
            ActionPlanner.scheduleNextDays(getApplicationContext(), db, ActionPlanHorizon.getDaysAhead(this, db));
            runOnUiThread(this::reloadTree);
        }).start();
    }

    private interface LongConsumer {
        void accept(long utcMs);
    }
}