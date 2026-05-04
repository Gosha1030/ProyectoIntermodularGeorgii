package georgii.sytnik.thothtasks.ui.schedule;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import georgii.sytnik.thothtasks.R;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.VH> {

    private final List<ScheduleTaskRow> rows;

    public ScheduleAdapter(List<ScheduleTaskRow> rows) {
        this.rows = rows;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_schedule_task, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ScheduleTaskRow r = rows.get(position);

        h.tvTime.setText(r.timeText);
        h.tvTitle.setText(r.task.taskName);
        h.tvSub.setText(r.subText);

        // muted (but active) -> grey
        if (r.task.state && r.task.muted) {
            h.itemView.setAlpha(0.55f);
            h.tvTitle.setPaintFlags(h.tvTitle.getPaintFlags() | Paint.ANTI_ALIAS_FLAG);
        } else {
            h.itemView.setAlpha(1.0f);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTime, tvTitle, tvSub;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSub = itemView.findViewById(R.id.tvSub);
        }
    }
}