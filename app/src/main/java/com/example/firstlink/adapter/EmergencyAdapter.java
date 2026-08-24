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
import com.example.firstlink.model.EmergencyRequest;
import com.example.firstlink.util.Photo;
import com.google.android.material.button.MaterialButton;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class EmergencyAdapter extends RecyclerView.Adapter<EmergencyAdapter.ViewHolder> {

    public interface OnEmergencyClickListener {
        void onEmergencyClick(EmergencyRequest emergency);

        void onResolveClick(EmergencyRequest emergency);

        void onApprovalDecision(EmergencyRequest emergency, boolean approved);
    }

    private final List<EmergencyRequest> emergencies;
    private final OnEmergencyClickListener listener;
    private final boolean isOrganizer;

    public EmergencyAdapter(List<EmergencyRequest> emergencies, boolean isOrganizer,
                            OnEmergencyClickListener listener) {
        this.emergencies = emergencies;
        this.isOrganizer = isOrganizer;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_emergency, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final EmergencyRequest emergency = emergencies.get(position);

        holder.location.setText(emergency.getLocation());
        holder.patientStatus.setText(
                emergency.getConsciousness() + " • " + emergency.getBreathing());
        String description = emergency.getDescription();
        holder.description.setText(description == null || description.isEmpty()
                ? "No description provided" : description);
        holder.time.setText("Reported at " + DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(new Date(emergency.getTimestamp())));

        List<String> acceptedBy = emergency.getAcceptedBy();
        holder.status.setText(emergency.getStatus()
                + (acceptedBy.isEmpty()
                        ? " — tap to respond"
                        : " — accepted by " + TextUtils.join(", ", acceptedBy)
                          + " — tap to join"));
        Photo.show(holder.photo, emergency.getPhoto());

        String reporter = emergency.getReporterName();
        holder.reporter.setText(reporter == null || reporter.trim().isEmpty()
                ? "Reported by " + emergency.getReporterRole()
                : "Reported by " + reporter);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onEmergencyClick(emergency);
            }
        });

        holder.resolveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onResolveClick(emergency);
            }
        });

        bindApproval(holder, emergency);
    }

    /**
     * The 911 approval row only means anything for events that require approval,
     * so it stays hidden until the reporter has actually asked. Only the organizer
     * gets the buttons; everyone else just sees where the request stands.
     */
    private void bindApproval(ViewHolder holder, final EmergencyRequest emergency) {
        String approval = emergency.getCallApprovalStatus();

        if (EmergencyRequest.APPROVAL_NOT_REQUESTED.equals(approval)) {
            holder.approvalStatus.setVisibility(View.GONE);
            holder.approvalButtons.setVisibility(View.GONE);
            return;
        }

        holder.approvalStatus.setVisibility(View.VISIBLE);
        boolean pending = EmergencyRequest.APPROVAL_PENDING.equals(approval);

        if (pending) {
            holder.approvalStatus.setText(isOrganizer
                    ? "911 call requested — your approval needed"
                    : "911 call: waiting for organizer approval");
            holder.approvalStatus.setTextColor(0xFFB45309);
        } else if (EmergencyRequest.APPROVAL_APPROVED.equals(approval)) {
            holder.approvalStatus.setText("911 call approved");
            holder.approvalStatus.setTextColor(0xFF16A34A);
        } else {
            holder.approvalStatus.setText("911 call denied by organizer");
            holder.approvalStatus.setTextColor(0xFF6B7280);
        }

        holder.approvalButtons.setVisibility(pending && isOrganizer ? View.VISIBLE : View.GONE);
        holder.approveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onApprovalDecision(emergency, true);
            }
        });
        holder.denyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onApprovalDecision(emergency, false);
            }
        });
    }

    @Override
    public int getItemCount() {
        return emergencies.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView location;
        final TextView patientStatus;
        final TextView description;
        final TextView time;
        final TextView status;
        final TextView reporter;
        final ImageView photo;
        final MaterialButton resolveButton;
        final TextView approvalStatus;
        final View approvalButtons;
        final MaterialButton approveButton;
        final MaterialButton denyButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            approvalStatus = itemView.findViewById(R.id.item_emergency_approval_status);
            approvalButtons = itemView.findViewById(R.id.item_emergency_approval_buttons);
            approveButton = itemView.findViewById(R.id.item_emergency_approve_button);
            denyButton = itemView.findViewById(R.id.item_emergency_deny_button);
            location = itemView.findViewById(R.id.item_emergency_location);
            patientStatus = itemView.findViewById(R.id.item_emergency_patient_status);
            description = itemView.findViewById(R.id.item_emergency_description);
            time = itemView.findViewById(R.id.item_emergency_time);
            status = itemView.findViewById(R.id.item_emergency_status);
            reporter = itemView.findViewById(R.id.item_emergency_reporter);
            photo = itemView.findViewById(R.id.item_emergency_photo);
            resolveButton = itemView.findViewById(R.id.item_emergency_resolve_button);
        }
    }
}
