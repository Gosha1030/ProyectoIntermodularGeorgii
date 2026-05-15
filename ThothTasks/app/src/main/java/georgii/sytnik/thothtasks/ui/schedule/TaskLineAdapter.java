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

public class TaskLineAdapter extends RecyclerView.Adapter<TaskLineAdapter.VH> {

    private final List<Line> lines;
    private final OnLineClick onLineClick;

    public TaskLineAdapter(List<Line> lines) {
        this(lines, null);
    }
    public TaskLineAdapter(List<Line> lines, OnLineClick onLineClick) {
        this.lines = lines;
        this.onLineClick = onLineClick;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task_line, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Line l = lines.get(position);
        h.tv.setText(l.text);
        if (l.muted) {
            h.itemView.setAlpha(0.55f);
            h.tv.setPaintFlags(h.tv.getPaintFlags() | Paint.ANTI_ALIAS_FLAG);
        } else {
            h.itemView.setAlpha(1.0f);
        }

        if (l.clickable && onLineClick != null) {
            h.itemView.setClickable(true);
            h.tv.setPaintFlags(h.tv.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            h.itemView.setOnClickListener(v -> onLineClick.onClick(l));
        } else {
            h.tv.setPaintFlags(h.tv.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
            h.itemView.setOnClickListener(null);
            h.itemView.setClickable(false);
        }
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    public interface OnLineClick {
        void onClick(Line line);
    }

    public record Line(String text, boolean muted, boolean clickable, Object payload) {
            public Line(String text, boolean muted) {
                this(text, muted, false, null);
            }

    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tv;

        VH(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tvLine);
        }
    }
}