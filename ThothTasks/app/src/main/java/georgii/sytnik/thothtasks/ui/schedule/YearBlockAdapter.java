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

public class YearBlockAdapter extends RecyclerView.Adapter<YearBlockAdapter.VH> {

    private final List<MonthBlock> blocks;
    private final ScheduleNavigator nav;

    public YearBlockAdapter(List<MonthBlock> blocks, ScheduleNavigator nav) {
        this.blocks = blocks;
        this.nav = nav;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_year_month_block, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MonthBlock b = blocks.get(position);
        h.tvHeader.setText(b.header);

        h.rv.setLayoutManager(new LinearLayoutManager(h.itemView.getContext()));
        h.rv.setAdapter(new TaskLineAdapter(b.lines));
        h.tvEmpty.setVisibility(b.lines.isEmpty() ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> nav.navigateToMonth(b.firstDayOfMonth));
    }

    @Override
    public int getItemCount() {
        return blocks.size();
    }

    public record MonthBlock(String header, Calendar firstDayOfMonth,
                             List<TaskLineAdapter.Line> lines) {
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvHeader, tvEmpty;
        RecyclerView rv;

        VH(@NonNull View itemView) {
            super(itemView);
            tvHeader = itemView.findViewById(R.id.tvMonthHeader);
            tvEmpty = itemView.findViewById(R.id.tvEmpty);
            rv = itemView.findViewById(R.id.rvTasks);
        }
    }
}