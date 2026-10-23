package com.example.firstlink.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.firstlink.R;
import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.HelpRequest;
import com.example.firstlink.util.Photo;
import com.google.android.material.button.MaterialButton;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class HelpRequestAdapter extends RecyclerView.Adapter<HelpRequestAdapter.ViewHolder> {

    public interface OnHelpClickListener {
        void onAcceptClick(HelpRequest request);

        void onResolveClick(HelpRequest request);
    }

    private final List<HelpRequest> requests;
    private final OnHelpClickListener listener;
    private final FirstLinkRepository repo;

    public HelpRequestAdapter(List<HelpRequest> requests, FirstLinkRepository repo,
                              OnHelpClickListener listener) {
        this.requests = requests;
        this.repo = repo;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_help_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final HelpRequest request = requests.get(position);

        String problem = request.getProblem();
        holder.problem.setVisibility(problem == null || problem.isEmpty()
                ? View.GONE : View.VISIBLE);
        holder.problem.setText(problem);

        String location = request.getLocation();
        holder.location.setText(location == null || location.isEmpty()
                ? "No location given" : location);
        holder.description.setText(request.getDescription());
        holder.time.setText("Submitted at " + DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(new Date(request.getTimestamp())));
        holder.status.setText(request.getStatus());
        Photo.show(holder.photo, request.getPhoto());

        List<String> acceptedBy = request.getAcceptedBy();
        holder.accepted.setVisibility(acceptedBy.isEmpty() ? View.GONE : View.VISIBLE);
        holder.accepted.setText("Accepted by " + TextUtils.join(", ", acceptedBy));

        // Every staff member sees who to contact the moment the request lands,
        // whether or not anyone has accepted it yet.
        String contact = repo.contactPersonFor(problem);
        boolean showContact = contact != null && !contact.isEmpty();
        holder.contact.setVisibility(showContact ? View.VISIBLE : View.GONE);
        holder.contact.setText("Contact: " + contact);

        boolean mine = repo.acceptedByMe(request);

        // Others may still join in — the button only disappears for people already on it.
        holder.acceptButton.setVisibility(mine ? View.GONE : View.VISIBLE);
        holder.acceptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onAcceptClick(request);
            }
        });

        holder.resolveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onResolveClick(request);
            }
        });
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView problem;
        final TextView location;
        final TextView description;
        final TextView time;
        final TextView status;
        final TextView accepted;
        final TextView contact;
        final ImageView photo;
        final MaterialButton acceptButton;
        final MaterialButton resolveButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            problem = itemView.findViewById(R.id.item_help_problem);
            location = itemView.findViewById(R.id.item_help_location);
            description = itemView.findViewById(R.id.item_help_description);
            time = itemView.findViewById(R.id.item_help_time);
            status = itemView.findViewById(R.id.item_help_status);
            accepted = itemView.findViewById(R.id.item_help_accepted);
            contact = itemView.findViewById(R.id.item_help_contact);
            photo = itemView.findViewById(R.id.item_help_photo);
            acceptButton = itemView.findViewById(R.id.item_help_accept_button);
            resolveButton = itemView.findViewById(R.id.item_help_resolve_button);
        }
    }
}
