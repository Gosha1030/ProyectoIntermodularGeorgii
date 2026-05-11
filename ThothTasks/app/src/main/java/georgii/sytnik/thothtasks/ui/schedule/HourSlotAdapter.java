package georgii.sytnik.thothtasks.ui.schedule;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import georgii.sytnik.thothtasks.R;

public class HourSlotAdapter extends RecyclerView.Adapter<HourSlotAdapter.VH> {

    public static class Chip {
        public final String text;
        public final boolean muted;
        public Chip(String text, boolean muted) { this.text = text; this.muted = muted; }
    }

    private final int startHour; // 0..11 or 12..23
    private final List<List<Chip>> chipsPerHour; // size=12, each list is chips in that hour
    private final LayoutInflater inflater;

    public HourSlotAdapter(Context ctx, int startHour, List<List<Chip>> chipsPerHour) {
        this.startHour = startHour;
        this.chipsPerHour = chipsPerHour;
        this.inflater = LayoutInflater.from(ctx);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.item_hour_slot, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        int hour = startHour + position;
        h.tvHour.setText(String.format("%02d:00", hour));

        h.container.removeAllViews();
        List<Chip> chips = chipsPerHour.get(position);
        for (Chip c : chips) {
            View chipView = inflater.inflate(R.layout.item_task_chip, h.container, false);
            TextView tv = chipView.findViewById(R.id.tvChip);
            tv.setText(c.text);
            chipView.setAlpha(c.muted ? 0.55f : 1.0f);
            h.container.addView(chipView);
        }
    }

    @Override public int getItemCount() { return 12; }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvHour;
        LinearLayout container;
        VH(@NonNull View itemView) {
            super(itemView);
            tvHour = itemView.findViewById(R.id.tvHour);
            container = itemView.findViewById(R.id.containerTasks);
        }
    }
}