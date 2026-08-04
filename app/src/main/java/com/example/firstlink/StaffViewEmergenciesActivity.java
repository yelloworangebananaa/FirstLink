package com.example.firstlink;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.firstlink.adapter.EmergencyAdapter;
import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.EmergencyRequest;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class StaffViewEmergenciesActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private TextView emptyStateText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.staff_view_emergencies);

        recyclerView = findViewById(R.id.emergencies_recycler_view);
        emptyStateText = findViewById(R.id.emergencies_empty_state);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Repaint when another device reports or accepts something.
        FirstLinkRepository.get(this).setOnChangeListener(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        });
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        FirstLinkRepository.get(this).setOnChangeListener(null);
    }

    private void refresh() {
        List<EmergencyRequest> emergencies =
                FirstLinkRepository.get(this).getOpenEmergencies();
        emptyStateText.setVisibility(emergencies.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(emergencies.isEmpty() ? View.GONE : View.VISIBLE);
        recyclerView.setAdapter(new EmergencyAdapter(emergencies,
                FirstLinkRepository.get(this).isOrganizer(),
                new EmergencyAdapter.OnEmergencyClickListener() {
                    @Override
                    public void onEmergencyClick(EmergencyRequest emergency) {
                        // Fallback path to the responder alert screen when
                        // the notification was missed or permission denied.
                        if (EmergencyRequest.STATUS_ACTIVE.equals(emergency.getStatus())) {
                            Intent intent = new Intent(StaffViewEmergenciesActivity.this,
                                    EmergencyResponderAlertActivity.class);
                            intent.putExtra(EmergencyResponderAlertActivity.EXTRA_EMERGENCY_ID,
                                    emergency.getId());
                            startActivity(intent);
                        }
                    }

                    @Override
                    public void onResolveClick(EmergencyRequest emergency) {
                        confirmResolve(emergency);
                    }

                    @Override
                    public void onApprovalDecision(EmergencyRequest emergency, boolean approved) {
                        confirmApproval(emergency, approved);
                    }
                }));
    }

    /** Confirmed both ways: approving authorises a real 911 call, denying blocks one. */
    private void confirmApproval(final EmergencyRequest emergency, final boolean approved) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(approved ? "Approve 911 call?" : "Deny 911 call?")
                .setMessage(approved
                        ? "The person reporting the emergency at " + emergency.getLocation()
                          + " will be allowed to dial 911."
                        : "The reporter will be told a 911 call was not approved. The emergency "
                          + "stays active and responders remain alerted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(approved ? "Approve" : "Deny", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        FirstLinkRepository.get(StaffViewEmergenciesActivity.this)
                                .setCallApproval(emergency.getId(), approved);
                        Toast.makeText(StaffViewEmergenciesActivity.this,
                                approved ? "911 call approved" : "911 call denied",
                                Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                })
                .show();
    }

    /** Confirmed, because an accidental tap would hide a live emergency from staff. */
    private void confirmResolve(final EmergencyRequest emergency) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Mark resolved?")
                .setMessage("This removes the emergency at " + emergency.getLocation()
                        + " from the active list. Only do this once the patient has been "
                        + "handed over or no longer needs help.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Mark Resolved", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        FirstLinkRepository.get(StaffViewEmergenciesActivity.this)
                                .resolveEmergency(emergency.getId());
                        Toast.makeText(StaffViewEmergenciesActivity.this,
                                "Emergency resolved", Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                })
                .show();
    }
}
