package com.example.firstlink;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import com.example.firstlink.data.FirstLinkRepository;
import com.example.firstlink.data.ReadyMap;
import com.example.firstlink.model.Event;
import com.example.firstlink.model.User;
import com.example.firstlink.util.GlassBlur;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * ReadyMap Lite, organizer only: how prepared each place is, from what was entered.
 * Edits write straight through the repository; drills only read.
 */
public class ReadyMapActivity extends BaseActivity {

    private GridLayout grid;
    private TextView summary;
    private TextView unpostedBanner;
    private TextView emptyState;

    // The place editor stays live too, so a change from another device shows in it.
    private AlertDialog spotDialog;
    private View spotView;
    private String spotLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ready_map);
        grid = findViewById(R.id.ready_map_grid);
        summary = findViewById(R.id.ready_map_summary);
        unpostedBanner = findViewById(R.id.ready_map_unposted);
        emptyState = findViewById(R.id.ready_map_empty_state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        FirstLinkRepository.get(this).setOnChangeListener(this::refresh);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        FirstLinkRepository.get(this).setOnChangeListener(null);
    }

    private void refresh() {
        FirstLinkRepository repo = FirstLinkRepository.get(this);
        Event event = repo.getEvent();
        if (event == null) {
            finish();
            return;
        }
        List<User> people = repo.getParticipants();
        List<ReadyMap.Spot> spots = ReadyMap.coverage(event, people);

        int covered = 0, partial = 0, gaps = 0;
        grid.removeAllViews();
        for (ReadyMap.Spot spot : spots) {
            if (spot.level == ReadyMap.Level.COVERED) covered++;
            else if (spot.level == ReadyMap.Level.PARTIAL) partial++;
            else gaps++;
            grid.addView(tile(spot));
        }
        summary.setText(covered + " covered · " + partial + " partial · "
                + gaps + (gaps == 1 ? " gap" : " gaps"));

        boolean empty = spots.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        summary.setVisibility(empty ? View.GONE : View.VISIBLE);
        grid.setVisibility(empty ? View.GONE : View.VISIBLE);

        int unposted = ReadyMap.unposted(event, people).size();
        unpostedBanner.setVisibility(unposted == 0 || empty ? View.GONE : View.VISIBLE);
        unpostedBanner.setText(unposted + (unposted == 1 ? " responder" : " responders")
                + " not posted yet");

        if (spotDialog != null && spotDialog.isShowing()) bindSpot();
    }

    private View tile(final ReadyMap.Spot spot) {
        View tile = LayoutInflater.from(this).inflate(R.layout.item_ready_spot, grid, false);
        TextView status = tile.findViewById(R.id.ready_spot_status);
        // Text label on every tile: colour is never the only signal. Never red - red is
        // the emergency path's colour (Von Restorff).
        if (spot.level == ReadyMap.Level.COVERED) {
            tile.setBackgroundResource(R.drawable.bg_ios_card_glass_green);
            status.setBackgroundResource(R.drawable.bg_ios_pill_green);
            status.setTextColor(ContextCompat.getColor(this, R.color.success));
            status.setText("Covered");
        } else if (spot.level == ReadyMap.Level.PARTIAL) {
            status.setText("Partial");
        } else {
            tile.setBackgroundResource(R.drawable.bg_ios_code_orange);
            status.setText("Gap");
        }
        ((TextView) tile.findViewById(R.id.ready_spot_name)).setText(spot.location);

        int n = spot.responders.size();
        indicator(tile.findViewById(R.id.ready_spot_responders), n > 0,
                n == 0 ? "No responders" : n + (n == 1 ? " responder" : " responders"));
        indicator(tile.findViewById(R.id.ready_spot_aed), spot.aed, spot.aed ? "AED" : "No AED");
        indicator(tile.findViewById(R.id.ready_spot_first_aid), spot.firstAid,
                spot.firstAid ? "First-aid kit" : "No first-aid kit");

        tile.setOnClickListener(v -> openSpot(spot.location));
        return tile;
    }

    private void indicator(TextView view, boolean present, String text) {
        int color = ContextCompat.getColor(this, present ? R.color.success : R.color.text_secondary);
        view.setText(text);
        view.setTextColor(color);
        TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(color));
    }

    private void openSpot(String location) {
        spotLocation = location;
        spotView = LayoutInflater.from(this).inflate(R.layout.dialog_ready_spot, null);
        spotView.findViewById(R.id.ready_spot_drill_button).setOnClickListener(v -> {
            // The place dialog is see-through glass; leaving it up shows through the drill.
            spotDialog.dismiss();
            showDrill(location);
        });
        bindSpot();
        spotDialog = GlassBlur.behindDialog(new MaterialAlertDialogBuilder(this)
                .setTitle(location)
                .setView(spotView)
                .setPositiveButton("Done", null)
                .show());
    }

    /** Fills the place editor from the repository. Rebuilt on every change. */
    private void bindSpot() {
        final FirstLinkRepository repo = FirstLinkRepository.get(this);
        Event event = repo.getEvent();
        if (event == null) return;
        final String location = spotLocation;

        CompoundButton aed = spotView.findViewById(R.id.ready_spot_aed_switch);
        aed.setOnCheckedChangeListener(null);
        aed.setChecked(event.getAedLocations().contains(location));
        aed.setOnCheckedChangeListener((b, on) -> repo.setAed(location, on));

        CompoundButton firstAid = spotView.findViewById(R.id.ready_spot_first_aid_switch);
        firstAid.setOnCheckedChangeListener(null);
        firstAid.setChecked(event.getFirstAidLocations().contains(location));
        firstAid.setOnCheckedChangeListener((b, on) -> repo.setFirstAid(location, on));

        LinearLayout list = spotView.findViewById(R.id.ready_spot_responder_list);
        list.removeAllViews();
        List<User> responders = ReadyMap.responders(repo.getParticipants());
        if (responders.isEmpty()) {
            TextView none = new TextView(this, null, 0, R.style.SettingsValueRow);
            none.setText("No responders have joined yet");
            none.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            list.addView(none);
        }
        for (int i = 0; i < responders.size(); i++) {
            final User u = responders.get(i);
            if (i > 0) {
                View separator = new View(this);
                separator.setBackgroundResource(R.drawable.bg_ios_separator);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        Math.round(getResources().getDisplayMetrics().density));
                lp.setMarginStart(Math.round(16 * getResources().getDisplayMetrics().density));
                list.addView(separator, lp);
            }
            String post = event.getResponderPosts().get(u.getId());
            boolean here = location.equals(post);
            String sub = here ? null
                    : post == null || !event.getLocations().contains(post) ? "Not posted"
                    : "Posted at " + post;

            CheckBox row = (CheckBox) LayoutInflater.from(this)
                    .inflate(R.layout.item_ready_responder, list, false);
            row.setText(twoLine(u.getSummary(), sub));
            row.setChecked(here);
            // Checking moves them here; unchecking clears their post.
            row.setOnCheckedChangeListener((b, on) -> repo.postResponder(u.getId(), on ? location : null));
            list.addView(row);
        }
    }

    private CharSequence twoLine(String title, String subtitle) {
        if (subtitle == null) return title;
        SpannableString s = new SpannableString(title + "\n" + subtitle);
        int start = title.length() + 1;
        s.setSpan(new RelativeSizeSpan(0.8f), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_secondary)),
                start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /** Read-only walk-through: no repository writes, no notifications. */
    private void showDrill(String location) {
        FirstLinkRepository repo = FirstLinkRepository.get(this);
        Event event = repo.getEvent();
        if (event == null) return;
        ReadyMap.Drill drill = ReadyMap.drill(event, repo.getParticipants(), location);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_ready_drill, null);

        List<String> here = new ArrayList<>();
        for (User u : drill.spot.responders) here.add(u.getDisplayName());
        ((TextView) view.findViewById(R.id.ready_drill_here)).setText(
                here.isEmpty() ? "Nobody posted here" : TextUtils.join("\n", here));

        List<String> elsewhere = new ArrayList<>();
        for (User u : drill.elsewhere) {
            String post = event.getResponderPosts().get(u.getId());
            boolean posted = post != null && event.getLocations().contains(post);
            elsewhere.add(u.getDisplayName() + " — " + (posted ? post : "Not posted"));
        }
        ((TextView) view.findViewById(R.id.ready_drill_elsewhere)).setText(
                elsewhere.isEmpty() ? "Nobody else to send" : TextUtils.join("\n", elsewhere));

        ((TextView) view.findViewById(R.id.ready_drill_aed)).setText(drill.spot.aed
                ? "AED on site here"
                : drill.aedLocations.isEmpty() ? "No AEDs entered"
                : "Nearest AEDs: " + TextUtils.join(", ", drill.aedLocations));

        ((TextView) view.findViewById(R.id.ready_drill_911)).setText(event.isRequire911Approval()
                ? "Organizer approval required before calling 911"
                : "Reporter is offered 911 immediately");

        GlassBlur.behindDialog(new MaterialAlertDialogBuilder(this)
                .setTitle("Drill: " + location)
                .setView(view)
                .setPositiveButton("End Drill", null)
                .show());
    }
}
