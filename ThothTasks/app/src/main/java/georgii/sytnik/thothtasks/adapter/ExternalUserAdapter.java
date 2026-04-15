package georgii.sytnik.thothtasks.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import georgii.sytnik.thothtasks.data.model.ExternalUser;
import georgii.sytnik.thothtasks.databinding.ItemExternalUserBinding;

public class ExternalUserAdapter extends RecyclerView.Adapter<ExternalUserAdapter.ExternalUserViewHolder> {

    private final List<ExternalUser> items;

    public ExternalUserAdapter(List<ExternalUser> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ExternalUserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemExternalUserBinding binding = ItemExternalUserBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ExternalUserViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ExternalUserViewHolder holder, int position) {
        ExternalUser item = items.get(position);

        holder.binding.tvExternalName.setText(item.getExternalUserName());
        holder.binding.tvExternalConnection.setText("IP: " + item.getIp() + " • Port: " + item.getPort());
        holder.binding.tvExternalType.setText(item.getType() == null ? "-" : item.getType());
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class ExternalUserViewHolder extends RecyclerView.ViewHolder {
        ItemExternalUserBinding binding;

        ExternalUserViewHolder(ItemExternalUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
