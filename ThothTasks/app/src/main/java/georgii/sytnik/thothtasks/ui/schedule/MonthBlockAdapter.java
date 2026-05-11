package georgii.sytnik.thothtasks.ui.schedule;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.List;

import georgii.sytnik.thothtasks.R;

public class MonthBlockAdapter extends RecyclerView.Adapter<MonthBlockAdapter.VH> {

    public static class WeekBlock {
        public final String header;
        public final Calendar weekStart;
        public final List<TaskLineAdapter.Line> lines;

        public WeekBlock(String header, Calendar weekStart, List<TaskLineAdapter.Line> lines) {
            this.header = header;
            this.weekStart = weekStart;
            this.lines = lines;
        }
    }

    private final List<WeekBlock> blocks;
    private final ScheduleNavigator nav;

    public MonthBlockAdapter(List<WeekBlock> blocks, ScheduleNavigator nav) {
        this.blocks = blocks;
        this.nav = nav;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_month_week_block, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        WeekBlock b = blocks.get(position);
        h.tvHeader.setText(b.header);

        h.rv.setLayoutManager(new LinearLayoutManager(h.itemView.getContext()));
        h.rv.setAdapter(new TaskLineAdapter(b.lines));
        h.tvEmpty.setVisibility(b.lines.isEmpty() ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> nav.navigateToWeek(b.weekStart));
    }

    @Override public int getItemCount() { return blocks.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvHeader, tvEmpty;
        RecyclerView rv;
        VH(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvWeekHeader);
            tvEmpty = itemView.findViewById(R.id.tvEmpty);
            rv = itemView.findViewById(R.id.rvTasks);
        }
    }
}