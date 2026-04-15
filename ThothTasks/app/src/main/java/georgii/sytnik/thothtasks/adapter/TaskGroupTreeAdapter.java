package georgii.sytnik.thothtasks.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import georgii.sytnik.thothtasks.data.model.TaskGroupNode;
import georgii.sytnik.thothtasks.databinding.ItemTaskGroupBinding;

public class TaskGroupTreeAdapter extends RecyclerView.Adapter<TaskGroupTreeAdapter.VH> {

    private final List<TaskGroupNode> rootNodes;
    private final List<DisplayNode> flatList = new ArrayList<>();

    public TaskGroupTreeAdapter(List<TaskGroupNode> roots) {
        this.rootNodes = roots;
        rebuildFlatList();
    }

    private void rebuildFlatList() {
        flatList.clear();
        for (TaskGroupNode node : rootNodes) {
            addNode(node, 0);
        }
    }

    private void addNode(TaskGroupNode node, int level) {
        flatList.add(new DisplayNode(node, level));
        if (node.expanded) {
            for (TaskGroupNode child : node.children) {
                addNode(child, level + 1);
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTaskGroupBinding binding = ItemTaskGroupBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new VH(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        DisplayNode item = flatList.get(position);
        TaskGroupNode node = item.node;

        holder.binding.tvGroupName.setText(node.group.getTaskGroupName());

        int leftPadding = item.level * 48;
        holder.binding.tvGroupName.setPadding(
                leftPadding,
                holder.binding.tvGroupName.getPaddingTop(),
                holder.binding.tvGroupName.getPaddingRight(),
                holder.binding.tvGroupName.getPaddingBottom()
        );

        if (node.children.isEmpty()) {
            holder.binding.tvExpand.setVisibility(View.INVISIBLE);
        } else {
            holder.binding.tvExpand.setVisibility(View.VISIBLE);
            holder.binding.tvExpand.setText(node.expanded ? "▼" : "▶");
        }

        holder.itemView.setOnClickListener(v -> {
            if (!node.children.isEmpty()) {
                node.expanded = !node.expanded;
                rebuildFlatList();
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public int getItemCount() {
        return flatList.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ItemTaskGroupBinding binding;

        VH(ItemTaskGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static class DisplayNode {
        TaskGroupNode node;
        int level;

        DisplayNode(TaskGroupNode node, int level) {
            this.node = node;
            this.level = level;
        }
    }
}
