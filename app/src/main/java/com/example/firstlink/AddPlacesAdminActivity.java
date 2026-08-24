package com.example.firstlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import com.example.firstlink.data.FirstLinkRepository;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class AddPlacesAdminActivity extends BaseActivity {

    private TextView emptyPlacesText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_places_admin);

        emptyPlacesText = findViewById(R.id.empty_places_text);
        MaterialButton addPlaceButton = findViewById(R.id.add_place_button);
        MaterialButton continueButton = findViewById(R.id.continue_button);

        addPlaceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(AddPlacesAdminActivity.this,
                        AfterAddPlacesClickedActivity.class));
            }
        });

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (FirstLinkRepository.get(AddPlacesAdminActivity.this).getLocations().isEmpty()) {
                    Toast.makeText(AddPlacesAdminActivity.this,
                            "Add at least one location before continuing",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(AddPlacesAdminActivity.this,
                        ImportantPeopleActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<String> places = FirstLinkRepository.get(this).getLocations();
        if (places.isEmpty()) {
            emptyPlacesText.setText("No locations have been added yet.");
        } else {
            StringBuilder sb = new StringBuilder("Saved locations:\n");
            for (String place : places) {
                sb.append("\n• ").append(place);
            }
            emptyPlacesText.setText(sb.toString());
        }
    }
}
