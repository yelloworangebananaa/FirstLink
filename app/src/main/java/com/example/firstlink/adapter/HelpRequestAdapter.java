package com.example.firstlink.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.firstlink.R;
import com.example.firstlink.model.HelpRequest;
import com.google.android.material.button.MaterialButton;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class HelpRequestAdapter extends RecyclerView.Adapter<HelpRequestAdapter.ViewHolder> {

    public interface OnResolveClickListener {
        void onResolveClick(HelpRequest request);
    }

    private final List<HelpRequest> requests;
    private final OnResolveClickListener listener;

    public HelpRequestAdapter(List<HelpRequest> requests, OnResolveClickListener listener) {
        this.requests = requests;
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

        String location = request.getLocation();
        holder.location.setText(location == null || location.isEmpty()
                ? "No location given" : location);
        holder.description.setText(request.getDescription());
        holder.time.setText("Submitted at " + DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(new Date(request.getTimestamp())));
        holder.status.setText(request.getStatus());

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
        final TextView location;
        final TextView description;
        final TextView time;
        final TextView status;
        final MaterialButton resolveButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            location = itemView.findViewById(R.id.item_help_location);
            description = itemView.findViewById(R.id.item_help_description);
            time = itemView.findViewById(R.id.item_help_time);
            status = itemView.findViewById(R.id.item_help_status);
            resolveButton = itemView.findViewById(R.id.item_help_resolve_button);
        }
    }
}
