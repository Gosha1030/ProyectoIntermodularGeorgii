package georgii.sytnik.thothtasks.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.AppDatabase;
import georgii.sytnik.thothtasks.db.entities.TaskEntity;
import georgii.sytnik.thothtasks.db.entities.UserEntity;
import georgii.sytnik.thothtasks.security.SessionStore;
import georgii.sytnik.thothtasks.ui.tree.NodeRow;
import georgii.sytnik.thothtasks.ui.tree.TaskTreeAdapter;

public class TaskPickerActivity extends AppCompatActivity {

    public static final String RESULT_TASK_ID = "result_task_id";
    public static final String RESULT_TASK_NAME = "result_task_name";

    // NEW: exclude a whole subtree (the edited task and its descendants)
    public static final String EXTRA_EXCLUDE_ROOT_ID = "exclude_root_id";

    private AppDatabase db;
    private final List<NodeRow> rows = new ArrayList<>();
    private TaskTreeAdapter adapter;

    private byte[] rootId;
    private NodeRow selected;

    private byte[] excludeRootId;
    private Set<String> excludedSet; // store as hex strings for simplicity

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_picker);

        db = AppDatabase.get(this);
        excludeRootId = getIntent().getByteArrayExtra(EXTRA_EXCLUDE_ROOT_ID);

        RecyclerView rv = findViewById(R.id.rvPick);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TaskTreeAdapter(rows, new TaskTreeAdapter.Listener() {
            @Override
            public void onToggle(NodeRow row, int position) {
                toggleRow(row, position);
            }

            @Override
            public void onClick(NodeRow row) {
                selected = row;
            }

            @Override
            public void onLongPress(NodeRow row, View anchor) {
                // no-op
            }
        });
        rv.setAdapter(adapter);

        MaterialButton ok = findViewById(R.id.btnOk);
        ok.setOnClickListener(v -> {
            if (selected == null) {
                Toast.makeText(this, R.string.toast_select_father, Toast.LENGTH_SHORT).show();
                return;
            }
            Intent data = new Intent();
            data.putExtra(RESULT_TASK_ID, selected.task.taskId);
            data.putExtra(RESULT_TASK_NAME, selected.task.taskName);
            setResult(RESULT_OK, data);
            finish();
        });

        buildExcludedSetAndLoad();
    }

    private void buildExcludedSetAndLoad() {
        new Thread(() -> {
            byte[] userId = SessionStore.loadLastUserId(this);
            if (userId == null) return;
            UserEntity u = db.userDao().findById(userId);
            if (u == null) return;

            rootId = u.taskRoot;

            excludedSet = new HashSet<>();
            if (excludeRootId != null) {
                // build a set of taskIds that are in the excluded subtree
                collectSubtree(excludeRootId);
            }

            rows.clear();
            List<TaskEntity> top = db.taskDao().childrenActiveOf(rootId);
            for (TaskEntity t : top) {
                if (isExcluded(t.taskId)) continue;
                NodeRow r = new NodeRow(t, 0);
                r.hasChildren = hasAnyChildNotExcluded(t.taskId);
                rows.add(r);
            }

            runOnUiThread(adapter::notifyDataSetChanged);
        }).start();
    }

    private void toggleRow(NodeRow row, int position) {
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
                List<TaskEntity> children = db.taskDao().childrenActiveOf(row.task.taskId);
                List<NodeRow> insert = new ArrayList<>();
                for (TaskEntity c : children) {
                    if (isExcluded(c.taskId)) continue;
                    NodeRow nr = new NodeRow(c, row.level + 1);
                    nr.hasChildren = hasAnyChildNotExcluded(c.taskId);
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

    private boolean hasAnyChildNotExcluded(byte[] fatherId) {
        List<TaskEntity> children = db.taskDao().childrenActiveOf(fatherId);
        for (TaskEntity c : children) {
            if (!isExcluded(c.taskId)) return true;
        }
        return false;
    }

    private void collectSubtree(byte[] subtreeRoot) {
        // DFS with stack
        List<byte[]> stack = new ArrayList<>();
        stack.add(subtreeRoot);
        while (!stack.isEmpty()) {
            byte[] id = stack.remove(stack.size() - 1);
            excludedSet.add(hex(id));

            List<TaskEntity> children = db.taskDao().childrenActiveOf(id);
            for (TaskEntity c : children) {
                stack.add(c.taskId);
            }
        }
    }

    private boolean isExcluded(byte[] id) {
        return excludedSet != null && excludedSet.contains(hex(id));
    }

    private static String hex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}