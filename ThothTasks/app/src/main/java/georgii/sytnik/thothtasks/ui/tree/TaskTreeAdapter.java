package georgii.sytnik.thothtasks.ui.tree;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.util.TaskTypeUi;

public class TaskTreeAdapter extends RecyclerView.Adapter<TaskTreeAdapter.VH> {

    public interface Listener {
        void onToggle(NodeRow row, int position);
        void onClick(NodeRow row);
        void onLongPress(NodeRow row, View anchor);
    }

    private final List<NodeRow> rows;
    private final Listener listener;

    public TaskTreeAdapter(List<NodeRow> rows, Listener listener) {
        this.rows = rows;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task_node, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        NodeRow row = rows.get(position);

        // Indentation by level (left padding)
        int padLeft = dp(h.itemView, 8 + row.level * 18);
        h.itemView.setPadding(
                padLeft,
                h.itemView.getPaddingTop(),
                h.itemView.getPaddingRight(),
                h.itemView.getPaddingBottom()
        );

        // Title
        h.tvName.setText(row.task.taskName);

        // Subtitle: localized task type label (Unique/Daily/... -> One-time/Daily... or Única/Diaria...)
        h.tvType.setText(TaskTypeUi.label(h.itemView.getContext(), row.task.type));

        // Expand icon
        h.ivExpand.setVisibility(row.hasChildren ? View.VISIBLE : View.INVISIBLE);
        h.ivExpand.setRotation(row.expanded ? 90f : 0f);

        // Muted visual state (effectiveMuted already resolved)
        if (row.effectiveMuted) {
            h.tvName.setAlpha(0.45f);
            h.tvType.setAlpha(0.35f);
            h.tvName.setPaintFlags(h.tvName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            h.tvName.setAlpha(1f);
            h.tvType.setAlpha(0.75f);
            h.tvName.setPaintFlags(h.tvName.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }

        // Clicks
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(row);
        });

        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onLongPress(row, h.itemView);
            return true;
        });

        // Expand/collapse on icon click (delegated to activity to rebuild list)
        h.ivExpand.setOnClickListener(v -> {
            if (listener != null) {
                int pos = h.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onToggle(row, pos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows != null ? rows.size() : 0;
    }

    static final class VH extends RecyclerView.ViewHolder {
        final ImageView ivExpand;
        final TextView tvName;
        final TextView tvType;

        VH(@NonNull View itemView) {
            super(itemView);
            ivExpand = itemView.findViewById(R.id.ivExpand);
            tvName = itemView.findViewById(R.id.tvName);
            tvType = itemView.findViewById(R.id.tvType);
        }
    }

    private static int dp(View v, int dp) {
        float d = v.getResources().getDisplayMetrics().density;
        return (int) (dp * d + 0.5f);
    }
}