package georgii.sytnik.thothtasks.ui.travel;

import android.content.Context;
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
import georgii.sytnik.thothtasks.util.HexBytes;

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

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_travel, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        TravelEntity t = items.get(position);
        Context ctx = h.itemView.getContext();

        String start = placeNameByIdHex.get(HexBytes.hex(t.startPlaceId));
        if (start == null) start = ctx.getString(R.string.travel_unknown_start);

        String finish = placeNameByIdHex.get(HexBytes.hex(t.finishPlaceId));
        if (finish == null) finish = ctx.getString(R.string.travel_unknown_finish);

        h.tvTitle.setText(ctx.getString(R.string.travel_title_format, start, finish));

        String dash = ctx.getString(R.string.dash);

        String type = (t.type == null || t.type.trim().isEmpty()) ? dash : t.type.trim();
        String user = (t.userTimeM == null) ? dash : String.valueOf(t.userTimeM);
        String google = (t.googleTimeM == null) ? dash : String.valueOf(t.googleTimeM);

        h.tvSub.setText(ctx.getString(
                R.string.travel_sub_format,
                type,
                t.timeM,
                user,
                google
        ));

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(t);
        });

        h.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(t);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvSub;
        final View btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSub = itemView.findViewById(R.id.tvSub);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}