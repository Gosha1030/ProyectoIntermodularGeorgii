package georgii.sytnik.thothtasks.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import georgii.sytnik.thothtasks.data.model.AppUser;
import georgii.sytnik.thothtasks.databinding.ItemUserBinding;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private final List<AppUser> users;

    public UserAdapter(List<AppUser> users) {
        this.users = users;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemUserBinding binding = ItemUserBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new UserViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        AppUser item = users.get(position);

        holder.binding.tvUserName.setText(item.getUserName());
        holder.binding.tvUserType.setText(item.getType().name());

        String details = "";
        if (item.getPort() != null) {
            details += "Port: " + item.getPort();
        }
        if (item.getIp() != null) {
            if (!details.isEmpty()) details += " • ";
            details += "IP: " + item.getIp();
        }

        holder.binding.tvUserDetails.setText(details.isEmpty() ? "-" : details);
        holder.binding.chipPasswordRequired.setText(item.isPasswordRequired() ? "Pwd ON" : "Pwd OFF");
    }

    @Override
    public int getItemCount() {
        return users == null ? 0 : users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        ItemUserBinding binding;

        UserViewHolder(ItemUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}