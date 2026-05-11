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

public class WeekBlockAdapter extends RecyclerView.Adapter<WeekBlockAdapter.VH> {

    public static class DayBlock {
        public final String header;
        public final Calendar day;
        public final List<TaskLineAdapter.Line> lines;

        public DayBlock(String header, Calendar day, List<TaskLineAdapter.Line> lines) {
            this.header = header;
            this.day = day;
            this.lines = lines;
        }
    }

    private final List<DayBlock> blocks;
    private final ScheduleNavigator nav;

    public WeekBlockAdapter(List<DayBlock> blocks, ScheduleNavigator nav) {
        this.blocks = blocks;
        this.nav = nav;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_week_day_block, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DayBlock b = blocks.get(position);
        h.tvHeader.setText(b.header);

        h.rv.setLayoutManager(new LinearLayoutManager(h.itemView.getContext()));
        h.rv.setAdapter(new TaskLineAdapter(b.lines));
        h.tvEmpty.setVisibility(b.lines.isEmpty() ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> nav.navigateToDay(b.day));
    }

    @Override public int getItemCount() { return blocks.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvHeader, tvEmpty;
        RecyclerView rv;
        VH(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvDayHeader);
            tvEmpty = itemView.findViewById(R.id.tvEmpty);
            rv = itemView.findViewById(R.id.rvTasks);
        }
    }
}