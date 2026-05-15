package georgii.sytnik.thothtasks.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.entities.ExternalSourceEntity;

public class ExternalSourceAdapter extends RecyclerView.Adapter<ExternalSourceAdapter.VH> {

    private final List<ExternalSourceEntity> sources;
    private final Listener listener;
    public ExternalSourceAdapter(List<ExternalSourceEntity> sources, Listener listener) {
        this.sources = sources;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_external_source, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ExternalSourceEntity s = sources.get(position);
        h.tvTitle.setText(s.displayName);

        String sub = s.ip + ":" + s.port + " • blocked=" + s.blocked +
                " • imported=" + (s.importedRootTaskId != null);
        h.tvSub.setText(sub);

        h.swInclude.setOnCheckedChangeListener(null);
        h.swInclude.setChecked(s.includedInSchedule && !s.blocked && s.importedRootTaskId != null);
        h.swInclude.setEnabled(!s.blocked && s.importedRootTaskId != null);

        h.swInclude.setOnCheckedChangeListener((btn, checked) -> listener.onToggle(s, checked));
        h.btnCheck.setOnClickListener(v -> listener.onCheck(s));
        h.btnSync.setOnClickListener(v -> listener.onSync(s));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(s));

        boolean imported = (s.importedRootTaskId != null);
        h.btnSync.setEnabled(!s.blocked);
        h.swInclude.setEnabled(!s.blocked && imported);

        h.itemView.setAlpha(s.blocked ? 0.55f : 1.0f);
    }

    @Override
    public int getItemCount() {
        return sources.size();
    }

    public interface Listener {
        void onToggle(ExternalSourceEntity src, boolean included);

        void onDelete(ExternalSourceEntity src);

        void onCheck(ExternalSourceEntity src);

        void onSync(ExternalSourceEntity src);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSub;
        SwitchCompat swInclude;
        View btnDelete;
        View btnCheck;
        View btnSync;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSub = itemView.findViewById(R.id.tvSub);
            swInclude = itemView.findViewById(R.id.swInclude);
            btnCheck = itemView.findViewById(R.id.btnCheck);
            btnSync = itemView.findViewById(R.id.btnSync);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
