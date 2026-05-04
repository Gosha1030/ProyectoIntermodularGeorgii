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

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task_node, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        NodeRow row = rows.get(position);
        h.tvName.setText(row.task.taskName);

        int indentPx = (int) (h.itemView.getResources().getDisplayMetrics().density * 16 * row.level);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) h.tvName.getLayoutParams();
        lp.leftMargin = indentPx;
        h.tvName.setLayoutParams(lp);

        if (!row.hasChildren) {
            h.ivExpand.setImageResource(0);
            h.ivExpand.setVisibility(View.INVISIBLE);
        } else {
            h.ivExpand.setVisibility(View.VISIBLE);
            h.ivExpand.setImageResource(row.expanded ? android.R.drawable.arrow_down_float : android.R.drawable.ic_media_next);
        }

        // Visual:
        // - muted & state=true => grey (tu preferencia)
        // - state=false (si se muestran via toggle) => más gris todavía
        if (!row.task.state) {
            h.itemView.setAlpha(0.40f);
        } else if (row.task.muted) {
            h.itemView.setAlpha(0.55f);
        } else {
            h.itemView.setAlpha(1.0f);
        }
        h.tvName.setPaintFlags(h.tvName.getPaintFlags() | Paint.ANTI_ALIAS_FLAG);

        h.ivExpand.setOnClickListener(v -> {
            if (row.hasChildren) listener.onToggle(row, position);
        });

        h.itemView.setOnClickListener(v -> listener.onClick(row));

        h.itemView.setOnLongClickListener(v -> {
            listener.onLongPress(row, v);
            return true;
        });
    }

    @Override public int getItemCount() { return rows.size(); }

    public static class VH extends RecyclerView.ViewHolder {
        ImageView ivExpand;
        TextView tvName;
        public VH(@NonNull View itemView) {
            super(itemView);
            ivExpand = itemView.findViewById(R.id.ivExpand);
            tvName = itemView.findViewById(R.id.tvName);
        }
    }
}
