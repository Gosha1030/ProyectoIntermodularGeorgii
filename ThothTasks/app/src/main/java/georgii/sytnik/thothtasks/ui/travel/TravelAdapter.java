package georgii.sytnik.thothtasks.ui.travel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.entities.TravelEntity;
import georgii.sytnik.thothtasks.net.MessageCodec;

public class TravelAdapter extends RecyclerView.Adapter<TravelAdapter.VH> {

    public interface Listener {
        void onClick(TravelEntity t);
        void onDelete(TravelEntity t);
    }

    private final List<TravelEntity> items;
    private final Map<String, String> placeNameByIdHex;
    private final Listener listener;

    public TravelAdapter(List<TravelEntity> items, Map<String, String> placeNameByIdHex, Listener listener) {
        this.items = items;
        this.placeNameByIdHex = placeNameByIdHex;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_travel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        TravelEntity t = items.get(position);

        String start = placeNameByIdHex.getOrDefault(MessageCodec.hex(t.startPlaceId), "Start?");
        String finish = placeNameByIdHex.getOrDefault(MessageCodec.hex(t.finishPlaceId), "Finish?");

        h.tvTitle.setText(start + " → " + finish);

        String type = (t.type == null || t.type.trim().isEmpty()) ? "-" : t.type.trim();
        String user = (t.userTimeM == null) ? "-" : String.valueOf(t.userTimeM);
        String google = (t.googleTimeM == null) ? "-" : String.valueOf(t.googleTimeM);

        h.tvSub.setText(type + " • " + t.timeM + "m • user=" + user + " • google=" + google);

        h.itemView.setOnClickListener(v -> listener.onClick(t));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(t));
    }

    @Override public int getItemCount() { return items.size(); }

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