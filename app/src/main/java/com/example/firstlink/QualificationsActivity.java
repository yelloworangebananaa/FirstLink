package com.example.firstlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;


import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.User;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class QualificationsActivity extends BaseActivity {

    private CheckBox cprCheck, firstAidCheck, aedCheck, medicalCheck, nurseCheck, emtCheck,
            otherCheck, noneCheck;
    private EditText otherInput;
    private EditText nameInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.qualifications);

        cprCheck = findViewById(R.id.qual_cpr);
        firstAidCheck = findViewById(R.id.qual_first_aid);
        aedCheck = findViewById(R.id.qual_aed);
        medicalCheck = findViewById(R.id.qual_medical);
        nurseCheck = findViewById(R.id.qual_nurse);
        emtCheck = findViewById(R.id.qual_emt);
        otherCheck = findViewById(R.id.qual_other);
        noneCheck = findViewById(R.id.qual_none);
        otherInput = findViewById(R.id.qual_other_text);
        nameInput = findViewById(R.id.qual_name);

        MaterialButton saveButton = findViewById(R.id.save_qualifications_button);
        MaterialButton skipButton = findViewById(R.id.skip_qualifications_button);

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveQualifications();
            }
        });

        skipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Skipping still records a name if one was typed, so staff can
                // tell participants apart even without qualifications.
                FirstLinkRepository.get(QualificationsActivity.this)
                        .saveProfile(nameInput.getText().toString().trim(),
                                new ArrayList<String>(), false);
                goToDashboard();
            }
        });
    }

    private void saveQualifications() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInput.setError("Enter your name so staff know who responded");
            return;
        }

        List<String> qualifications = new ArrayList<>();
        if (cprCheck.isChecked()) qualifications.add("CPR Certified");
        if (firstAidCheck.isChecked()) qualifications.add("First Aid Certified");
        if (aedCheck.isChecked()) qualifications.add("AED Trained");
        if (medicalCheck.isChecked()) qualifications.add("Medical Professional");
        if (nurseCheck.isChecked()) qualifications.add("Nurse");
        if (emtCheck.isChecked()) qualifications.add("EMT or Paramedic");
        if (otherCheck.isChecked()) {
            String other = otherInput.getText().toString().trim();
            qualifications.add(other.isEmpty() ? "Other" : other);
        }

        if (noneCheck.isChecked()) {
            qualifications.clear();
            qualifications.add("No Qualifications");
        } else if (qualifications.isEmpty()) {
            Toast.makeText(this,
                    "Select at least one qualification, or choose No Qualifications, or press Skip",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Anyone with a real qualification is eligible for emergency alerts.
        boolean qualifiedResponder = !noneCheck.isChecked();
        FirstLinkRepository.get(this).saveProfile(name, qualifications, qualifiedResponder);
        Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
        goToDashboard();
    }

    private void goToDashboard() {
        User user = FirstLinkRepository.get(this).getCurrentUser();
        Class<?> target = (user != null && user.isStaffLevel())
                ? StaffDashboardActivity.class
                : AttendeeMainDashboardActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }
}
