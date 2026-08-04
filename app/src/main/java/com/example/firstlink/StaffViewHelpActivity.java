package com.example.firstlink;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.firstlink.adapter.HelpRequestAdapter;
import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.model.HelpRequest;

import java.util.List;

public class StaffViewHelpActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private TextView emptyStateText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.staff_view_help);

        recyclerView = findViewById(R.id.help_recycler_view);
        emptyStateText = findViewById(R.id.help_empty_state);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Repaint when another device sends a help request.
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
        List<HelpRequest> requests = FirstLinkRepository.get(this).getOpenHelpRequests();
        emptyStateText.setVisibility(requests.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(requests.isEmpty() ? View.GONE : View.VISIBLE);
        recyclerView.setAdapter(new HelpRequestAdapter(requests,
                new HelpRequestAdapter.OnResolveClickListener() {
                    @Override
                    public void onResolveClick(HelpRequest request) {
                        FirstLinkRepository.get(StaffViewHelpActivity.this)
                                .resolveHelpRequest(request.getId());
                        Toast.makeText(StaffViewHelpActivity.this,
                                "Help request resolved", Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                }));
    }
}
