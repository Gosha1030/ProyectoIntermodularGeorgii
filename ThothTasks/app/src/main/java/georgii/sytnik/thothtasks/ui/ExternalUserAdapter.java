package georgii.sytnik.thothtasks.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.db.entities.ExternalUserEntity;

public class ExternalUserAdapter extends RecyclerView.Adapter<ExternalUserAdapter.VH> {

    private final List<ExternalUserEntity> users;
    private final Listener listener;

    public ExternalUserAdapter(List<ExternalUserEntity> users, Listener listener) {
        this.users = users;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_external_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ExternalUserEntity u = users.get(position);

        h.tvTitle.setText(u.externalUserName);

        String nick = (u.externalUserNickname == null || u.externalUserNickname.trim().isEmpty()) ? "-" : u.externalUserNickname.trim();

        String sub = u.ip + ":" + u.port + " • " + nick + " • blocked=" + u.blocked;
        h.tvSub.setText(sub);

        ((Button) h.btnBlock).setText(u.blocked ? "Unblock" : "Block");

        h.btnBlock.setOnClickListener(v -> listener.onToggleBlock(u));
        h.btnPerms.setOnClickListener(v -> listener.onManagePerms(u));

        h.itemView.setAlpha(u.blocked ? 0.55f : 1.0f);
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public interface Listener {
        void onToggleBlock(ExternalUserEntity user);

        void onManagePerms(ExternalUserEntity user);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSub;
        View btnBlock, btnPerms;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvSub = itemView.findViewById(R.id.tvSub);
            btnBlock = itemView.findViewById(R.id.btnBlock);
            btnPerms = itemView.findViewById(R.id.btnPerms);
        }
    }
}