package georgii.sytnik.thothtasks.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import georgii.sytnik.thothtasks.R;

public class SimpleRowAdapter extends RecyclerView.Adapter<SimpleRowAdapter.VH> {

    public interface Listener {
        void onClick(int position);
        void onDelete(int position);
    }

    public static class Row {
        public final String title;
        public final String sub;
        public Row(String title, String sub) { this.title = title; this.sub = sub; }
    }

    private final List<Row> rows;
    private final Listener listener;

    public SimpleRowAdapter(List<Row> rows, Listener listener) {
        this.rows = rows;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_simple_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = rows.get(position);
        h.tvTitle.setText(r.title);
        h.tvSub.setText(r.sub);

        h.itemView.setOnClickListener(v -> listener.onClick(position));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(position));
    }

    @Override public int getItemCount() { return rows.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSub;
        View btnDelete;
        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSub = itemView.findViewById(R.id.tvSub);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}