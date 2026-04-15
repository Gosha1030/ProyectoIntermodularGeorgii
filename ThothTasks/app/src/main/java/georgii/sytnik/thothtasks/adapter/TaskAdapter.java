package georgii.sytnik.thothtasks.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import georgii.sytnik.thothtasks.data.model.Task;
import georgii.sytnik.thothtasks.databinding.ItemTaskBinding;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private final List<Task> originalTasks;
    private final List<Task> visibleTasks;

    public TaskAdapter(List<Task> tasks) {
        this.originalTasks = new ArrayList<>(tasks);
        this.visibleTasks = new ArrayList<>(tasks);
    }

    public void filter(String query) {
        visibleTasks.clear();

        if (query == null || query.trim().isEmpty()) {
            visibleTasks.addAll(originalTasks);
        } else {
            String q = query.toLowerCase(Locale.getDefault()).trim();

            for (Task task : originalTasks) {
                String name = task.getTaskName() == null ? "" : task.getTaskName().toLowerCase(Locale.getDefault());
                String type = task.getType() == null ? "" : task.getType().name().toLowerCase(Locale.getDefault());
                String rule = task.getDateRule() == null ? "" : task.getDateRule().toLowerCase(Locale.getDefault());
                String where = task.getWhere() == null ? "" : task.getWhere().toLowerCase(Locale.getDefault());

                if (name.contains(q) || type.contains(q) || rule.contains(q) || where.contains(q)) {
                    visibleTasks.add(task);
                }
            }
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTaskBinding binding = ItemTaskBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new TaskViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task item = visibleTasks.get(position);

        holder.binding.tvTaskName.setText(item.getTaskName() == null ? "-" : item.getTaskName());
        holder.binding.tvTaskType.setText(item.getType() == null ? "-" : item.getType().name());
        holder.binding.tvTaskRule.setText(item.getDateRule() == null ? "-" : item.getDateRule());

        StringBuilder secondary = new StringBuilder();

        if (item.getStartTime() != null && !item.getStartTime().isEmpty()) {
            secondary.append(item.getStartTime());
        }

        if (item.getTimeM() != null) {
            if (secondary.length() > 0) secondary.append(" • ");
            secondary.append(item.getTimeM()).append(" min");
        }

        if (item.getWhere() != null && !item.getWhere().isEmpty()) {
            if (secondary.length() > 0) secondary.append(" • ");
            secondary.append(item.getWhere());
        }

        holder.binding.tvTaskInfo.setText(secondary.length() == 0 ? "-" : secondary.toString());
        holder.binding.chipState.setText(item.isState() ? "ON" : "OFF");
    }

    @Override
    public int getItemCount() {
        return visibleTasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        ItemTaskBinding binding;

        TaskViewHolder(ItemTaskBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}