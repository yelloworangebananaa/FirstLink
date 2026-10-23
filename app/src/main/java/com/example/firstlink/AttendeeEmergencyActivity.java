package com.example.firstlink;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;


import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.EmergencyRequest;
import com.example.firstlink.util.Dialer;
import com.example.firstlink.util.GlassBlur;
import com.example.firstlink.util.Photo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.util.List;

/** Shared by staff and attendees — the reporter returns to their own dashboard. */
public class AttendeeEmergencyActivity extends BaseActivity {

    private Spinner locationSpinner;
    private EditText descriptionInput;
    private RadioGroup consciousnessGroup;
    private RadioGroup breathingGroup;
    private ImageView photoPreview;
    private String photo;
    private FirstLinkRepository repo;
    private String awaitingApprovalId;
    private AlertDialog waitingDialog;
    private MaterialButton sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.attendee_emergency);

        repo = FirstLinkRepository.get(this);

        locationSpinner = findViewById(R.id.attendee_emergency_location_spinner);
        descriptionInput = findViewById(R.id.attendee_emergency_description);
        consciousnessGroup = findViewById(R.id.attendee_emergency_consciousness_group);
        breathingGroup = findViewById(R.id.attendee_emergency_breathing_group);
        photoPreview = findViewById(R.id.attendee_emergency_photo_preview);
        sendButton = findViewById(R.id.attendee_emergency_send_button);
        MaterialButton photoButton = findViewById(R.id.attendee_emergency_photo_button);

        photoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Photo.capture(AttendeeEmergencyActivity.this);
            }
        });

        // Locations entered by the organizer during event creation.
        List<String> locations = repo.getLocations();
        locations.add(0, "Select location...");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, locations);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationSpinner.setAdapter(adapter);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendEmergency();
            }
        });
    }

    private void sendEmergency() {
        if (Dashboards.bailOutIfEventGone(this)) return;
        if (locationSpinner.getSelectedItemPosition() <= 0) {
            Toast.makeText(this, "Select a location", Toast.LENGTH_SHORT).show();
            return;
        }
        String consciousness = selectedConsciousness();
        if (consciousness == null) {
            Toast.makeText(this, "Select the consciousness status", Toast.LENGTH_SHORT).show();
            return;
        }
        String breathing = selectedBreathing();
        if (breathing == null) {
            Toast.makeText(this, "Select the breathing status", Toast.LENGTH_SHORT).show();
            return;
        }

        String location = locationSpinner.getSelectedItem().toString();
        String description = descriptionInput.getText().toString().trim();

        sendButton.setEnabled(false);
        repo.addEmergency(location, description, consciousness, breathing, photo, (request, ok) -> {
            sendButton.setEnabled(true);
            if (ok) {
                // No notification for the reporter — they are standing right here. Every other
                // device raises its own alert from the realtime listener in the repository.
                showConfirmation(request);
            } else {
                showNotSent();
            }
        });
    }

    private void showNotSent() {
        GlassBlur.behindDialog(new MaterialAlertDialogBuilder(this)
                .setTitle("Not sent")
                .setCancelable(false)
                .setMessage("Your report did not reach the server. "
                        + "If someone is in danger, call 911 now.")
                .setNegativeButton("Retry", (dialog, which) -> sendEmergency())
                .setPositiveButton("Call 911", (dialog, which) ->
                        Dialer.confirmAndDial911(AttendeeEmergencyActivity.this))
                .show());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != Photo.REQUEST_CODE || resultCode != RESULT_OK) return;
        photo = Photo.encode(data);
        Photo.show(photoPreview, photo);
    }

    private String selectedConsciousness() {
        int id = consciousnessGroup.getCheckedRadioButtonId();
        if (id == R.id.attendee_emergency_conscious) return "Conscious";
        if (id == R.id.attendee_emergency_unconscious) return "Unconscious";
        if (id == R.id.attendee_emergency_consciousness_unknown) return "Unknown";
        return null;
    }

    private String selectedBreathing() {
        int id = breathingGroup.getCheckedRadioButtonId();
        if (id == R.id.attendee_emergency_breathing) return "Breathing";
        if (id == R.id.attendee_emergency_not_breathing) return "Not Breathing";
        if (id == R.id.attendee_emergency_breathing_unknown) return "Unknown";
        return null;
    }

    private void showConfirmation(EmergencyRequest request) {
        if (repo.needsApprovalBeforeCalling()) {
            requestOrganizerCallApproval(request);
            return;
        }

        GlassBlur.behindDialog(new MaterialAlertDialogBuilder(this)
                .setTitle("Emergency reported")
                .setCancelable(false)
                .setMessage("Qualified responders have been alerted.\n\n"
                        + "If this is life-threatening, call 911 now.")
                .setNegativeButton("Done", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        returnToDashboard();
                    }
                })
                .setPositiveButton("Call 911", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Dialer.confirmAndDial911(AttendeeEmergencyActivity.this);
                    }
                })
                .show());
    }

    /**
     * This event requires the organizer to approve a 911 call. Ask, then wait —
     * the organizer answers from their emergency list on their own device, and the
     * answer arrives here through the realtime listener.
     */
    private void requestOrganizerCallApproval(EmergencyRequest request) {
        awaitingApprovalId = request.getId();
        repo.requestCallApproval(awaitingApprovalId);

        waitingDialog = GlassBlur.behindDialog(new MaterialAlertDialogBuilder(this)
                .setTitle("Emergency reported")
                .setCancelable(false)
                .setMessage("Qualified responders have been alerted.\n\n"
                        + "This event requires organizer approval before calling 911. "
                        + "Your request has been sent — this screen updates as soon as they answer.")
                .setNegativeButton("Done", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        returnToDashboard();
                    }
                })
                .show());
    }

    /** Called whenever server data changes while this screen is open. */
    private void onApprovalMaybeChanged() {
        if (awaitingApprovalId == null) return;
        EmergencyRequest e = repo.getEmergencyById(awaitingApprovalId);
        if (e == null) return;

        EmergencyRequest.Approval approval = e.approval();
        if (approval == EmergencyRequest.Approval.APPROVED) {
            awaitingApprovalId = null;
            dismissWaitingDialog();
            GlassBlur.behindDialog(new MaterialAlertDialogBuilder(this)
                    .setTitle("911 call approved")
                    .setCancelable(false)
                    .setMessage("The organizer approved calling 911 for this emergency.")
                    .setNegativeButton("Not now", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            returnToDashboard();
                        }
                    })
                    .setPositiveButton("Call 911", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Dialer.confirmAndDial911(AttendeeEmergencyActivity.this);
                        }
                    })
                    .show());
        } else if (approval == EmergencyRequest.Approval.DENIED) {
            awaitingApprovalId = null;
            dismissWaitingDialog();
            GlassBlur.behindDialog(new MaterialAlertDialogBuilder(this)
                    .setTitle("911 call not approved")
                    .setCancelable(false)
                    .setMessage("The organizer did not approve a 911 call. The emergency is still "
                            + "active and responders have been alerted.")
                    .setPositiveButton("Done", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            returnToDashboard();
                        }
                    })
                    .show());
        }
    }

    private void dismissWaitingDialog() {
        if (waitingDialog != null && waitingDialog.isShowing()) {
            waitingDialog.dismiss();
        }
        waitingDialog = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Dashboards.bailOutIfEventGone(this)) return;
        repo.setOnChangeListener(new Runnable() {
            @Override
            public void run() {
                onApprovalMaybeChanged();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        repo.setOnChangeListener(null);
    }

    private void returnToDashboard() {
        Dashboards.returnTo(this);
    }
}
