package com.example.firstlink;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;


import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.User;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;

public class CreatorActivity extends BaseActivity {

    private TextInputEditText eventNameInput;
    private TextView venueNameInput;
    private TextInputEditText organizerNameInput;
    private SwitchMaterial approvalSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.creator);

        eventNameInput = findViewById(R.id.event_name_submission);
        venueNameInput = findViewById(R.id.venue_name_submission);
        findViewById(R.id.venue_picker_button).setOnClickListener(v -> showVenuePicker());
        organizerNameInput = findViewById(R.id.organizer_name_submission);
        approvalSwitch = findViewById(R.id.approval_switch);
        Button createButton = findViewById(R.id.create_event_btn);

        createButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createEvent();
            }
        });
    }

    private void createEvent() {
        String eventName = textOf(eventNameInput);
        String venueName = textOf(venueNameInput);
        String organizerName = textOf(organizerNameInput);

        boolean valid = true;
        if (eventName.isEmpty()) {
            eventNameInput.setError("Event name is required");
            valid = false;
        }
        if (venueName.isEmpty()) {
            venueNameInput.setError("Venue name is required");
            valid = false;
        }
        if (organizerName.isEmpty()) {
            organizerNameInput.setError("Organizer name is required");
            valid = false;
        }
        if (!valid) return;

        FirstLinkRepository repo = FirstLinkRepository.get(this);
        repo.createEvent(eventName, venueName, organizerName, approvalSwitch.isChecked());
        // The event creator is automatically staff (organizer role). They never see the
        // qualifications screen, so publish their name from the form instead.
        repo.createCurrentUser(User.ROLE_ORGANIZER);
        repo.saveProfile(organizerName, new ArrayList<String>(), false);

        startActivity(new Intent(this, AddPlacesAdminActivity.class));
    }

    private void showVenuePicker() {
        Dialog dialog = new Dialog(this, R.style.ThemeOverlay_FirstLink_VenueDialog);
        dialog.setContentView(R.layout.activity_venue_picker);
        dialog.setCanceledOnTouchOutside(true);

        // Size the window before the first show() so the dialog's ConstraintLayout (and the
        // MapView inside it) gets its final height on the first layout pass instead of
        // collapsing to 0dp (WRAP_CONTENT + weight) and being resized out from under osmdroid.
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.bg_venue_picker_dialog);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.62f);
            window.setLayout(width, height);
            window.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.28f;
            window.setAttributes(params);
        }

        VenuePickerActivity.configurePicker(dialog, this, venue -> {
            venueNameInput.setText(venue);
            dialog.dismiss();
        });

        dialog.show();
    }

    private static String textOf(TextView input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
