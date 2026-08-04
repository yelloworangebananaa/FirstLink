package com.example.firstlink;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;


import com.example.firstlink.data.FirstLinkRepository;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class AfterAddPlacesClickedActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.after_add_places_clicked);

        final EditText placesInput = findViewById(R.id.places_added);
        MaterialButton saveButton = findViewById(R.id.saveButton);

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String raw = placesInput.getText().toString();
                List<String> places = new ArrayList<>();
                for (String line : raw.split("\n")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        places.add(trimmed);
                    }
                }
                if (places.isEmpty()) {
                    Toast.makeText(AfterAddPlacesClickedActivity.this,
                            "Enter at least one place (one per line)",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                FirstLinkRepository.get(AfterAddPlacesClickedActivity.this).addLocations(places);
                Toast.makeText(AfterAddPlacesClickedActivity.this,
                        places.size() + " place(s) saved", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}
