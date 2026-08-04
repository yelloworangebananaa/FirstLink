package com.example.firstlink;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Wires the top-left back arrow for any screen whose layout contains a view with
 * id {@code back_button}. Done here rather than in each activity so adding the
 * button to a layout is all that is ever needed.
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        View back = findViewById(R.id.back_button);
        if (back != null) {
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
    }
}
