package com.example.firstlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.Contact;
import com.example.firstlink.model.User;
import com.example.firstlink.util.Photo;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

/** Shared by staff and attendees — the reporter returns to their own dashboard. */
public class AttendeeHelpActivity extends BaseActivity {

    private Spinner locationSpinner;
    private EditText descriptionInput;
    private ChipGroup problemChips;
    private ImageView photoPreview;
    private String photo;
    private FirstLinkRepository repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.attendee_help);

        repo = FirstLinkRepository.get(this);

        locationSpinner = findViewById(R.id.attendee_help_location_spinner);
        descriptionInput = findViewById(R.id.attendee_help_description);
        problemChips = findViewById(R.id.attendee_help_problem_chips);
        photoPreview = findViewById(R.id.attendee_help_photo_preview);
        MaterialButton sendButton = findViewById(R.id.attendee_help_send_button);
        MaterialButton photoButton = findViewById(R.id.attendee_help_photo_button);

        showProblemChips();

        photoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Photo.capture(AttendeeHelpActivity.this);
            }
        });

        List<String> locations = repo.getLocations();
        locations.add(0, "Select location (optional)");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, locations);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationSpinner.setAdapter(adapter);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendHelpRequest();
            }
        });
    }

    /**
     * One button per problem the organizer set up. Tapping one tags the request so
     * staff immediately see who handles it, and that person is notified directly.
     */
    private void showProblemChips() {
        List<Contact> contacts = repo.getContacts();
        TextView label = findViewById(R.id.attendee_help_problem_label);
        if (contacts.isEmpty()) {
            label.setVisibility(View.GONE);
            problemChips.setVisibility(View.GONE);
            return;
        }
        for (Contact contact : contacts) {
            Chip chip = new Chip(this);
            chip.setText(contact.getProblem());
            chip.setCheckable(true);
            problemChips.addView(chip);
        }
    }

    private String selectedProblem() {
        int checkedId = problemChips.getCheckedChipId();
        if (checkedId == View.NO_ID) return "";
        Chip chip = problemChips.findViewById(checkedId);
        return chip == null ? "" : chip.getText().toString();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != Photo.REQUEST_CODE || resultCode != RESULT_OK) return;
        photo = Photo.encode(data);
        Photo.show(photoPreview, photo);
    }

    private void sendHelpRequest() {
        String description = descriptionInput.getText().toString().trim();
        String problem = selectedProblem();
        // A tapped preset says enough on its own, so it stands in for the description.
        if (description.isEmpty() && problem.isEmpty()) {
            descriptionInput.setError("Describe what you need help with");
            return;
        }
        String location = locationSpinner.getSelectedItemPosition() <= 0
                ? "" : locationSpinner.getSelectedItem().toString();

        repo.addHelpRequest(location, description.isEmpty() ? problem : description,
                problem, photo);

        // Staff devices raise their own notification from the Firestore listener.
        Toast.makeText(this, "Help request sent to staff", Toast.LENGTH_LONG).show();

        User user = repo.getCurrentUser();
        Class<?> target = (user != null && user.isStaffLevel())
                ? StaffDashboardActivity.class
                : AttendeeMainDashboardActivity.class;
        Intent intent = new Intent(this, target);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
