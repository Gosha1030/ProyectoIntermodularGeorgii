package georgii.sytnik.thothtasks.ui.place;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.entities.PlaceEntity;

public class PlaceAdapter extends RecyclerView.Adapter<PlaceAdapter.VH> {

    public interface Listener {
        void onClick(PlaceEntity p);
        void onDelete(PlaceEntity p);
    }

    private final List<PlaceEntity> items;
    private final Listener listener;

    public PlaceAdapter(List<PlaceEntity> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_place, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PlaceEntity p = items.get(position);
        h.tvName.setText(p.placeName);
        h.itemView.setOnClickListener(v -> listener.onClick(p));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(p));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName;
        View btnDelete;
        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}