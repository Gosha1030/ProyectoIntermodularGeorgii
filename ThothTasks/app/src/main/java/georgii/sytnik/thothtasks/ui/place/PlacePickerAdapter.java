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

public class PlacePickerAdapter extends RecyclerView.Adapter<PlacePickerAdapter.VH> {

    public interface Listener {
        void onPick(PlaceEntity p);
    }

    private final List<PlaceEntity> items;
    private final Listener listener;

    public PlacePickerAdapter(List<PlaceEntity> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_place_picker, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PlaceEntity p = items.get(position);

        String name = (p.placeName == null || p.placeName.trim().isEmpty())
                ? h.itemView.getContext().getString(R.string.place_placeholder)
                : p.placeName;

        h.tv.setText(name);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPick(p);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tv;

        VH(@NonNull View itemView) {
            super(itemView);
            tv = itemView.findViewById(R.id.tvPlace);
        }
    }
}