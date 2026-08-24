package com.example.firstlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.Contact;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Organizer setup, step 3: "who do I call for X". Each problem added here becomes a
 * preset button on the help screen; the contact behind it is revealed only to the
 * staff member who accepts the request.
 *
 * Each person also gets their own join code. Joining with it makes them staff tagged
 * with their problem, so requests for it alert them by name as well as the staff.
 */
public class ImportantPeopleActivity extends BaseActivity {

    private EditText problemInput;
    private EditText personInput;
    private TextView listText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.important_people);

        problemInput = findViewById(R.id.important_problem_input);
        personInput = findViewById(R.id.important_person_input);
        listText = findViewById(R.id.important_list_text);
        MaterialButton addButton = findViewById(R.id.important_add_button);
        MaterialButton continueButton = findViewById(R.id.important_continue_button);

        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addContact();
            }
        });

        // Optional step: an event with no preset problems just shows no preset buttons.
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ImportantPeopleActivity.this,
                        RoomCreationActivity.class));
            }
        });

        showContacts();
    }

    private void addContact() {
        String problem = problemInput.getText().toString().trim();
        String person = personInput.getText().toString().trim();
        if (problem.isEmpty()) {
            problemInput.setError("Name the problem, e.g. Water problem");
            return;
        }
        if (person.isEmpty()) {
            personInput.setError("Name who handles it");
            return;
        }
        Contact contact = FirstLinkRepository.get(this).saveContact(problem, person);
        problemInput.setText("");
        personInput.setText("");
        problemInput.requestFocus();
        Toast.makeText(this, person + "'s code: " + contact.getCode(), Toast.LENGTH_LONG).show();
        showContacts();
    }

    private void showContacts() {
        List<Contact> contacts = FirstLinkRepository.get(this).getContacts();
        if (contacts.isEmpty()) {
            listText.setText("No problems have been added yet.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Contact contact : contacts) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("• ").append(contact.getProblem())
                    .append(" — ").append(contact.getPerson())
                    .append("\n   Join code: ").append(contact.getCode());
        }
        listText.setText(sb.toString());
    }
}
