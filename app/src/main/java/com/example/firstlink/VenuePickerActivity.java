package com.example.firstlink;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VenuePickerActivity extends AppCompatActivity {
    public static final String EXTRA_VENUE = "venue";
    // tile.openstreetmap.org 403s non-browser app traffic under OSM's usage policy; CartoDB's
    // free basemap tiles are meant for direct app use instead.
    private static final ITileSource VENUE_MAP_TILE_SOURCE = new XYTileSource(
            "CartoDBVoyager", 0, 20, 256, ".png",
            new String[]{
                    "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
                    "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
                    "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
                    "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
            },
            "© OpenStreetMap contributors © CARTO");
    private static final String PHOTON_USER_AGENT = "FirstLink/1.0 (contact: gshinkimclaude@gmail.com)";
    // Android's Geocoder is a full-address resolver with a flaky, device-dependent backend, so it
    // doesn't work for live suggestions. Photon is a free public geocoder built for search-as-you-type.
    private static final String PHOTON_SEARCH_URL = "https://photon.komoot.io/api/?limit=5&q=";
    private static final String PHOTON_REVERSE_URL = "https://photon.komoot.io/reverse?lat=%f&lon=%f";
    private static final long SEARCH_DEBOUNCE_MS = 300;

    private PickerController controller;

    public static void configurePicker(Dialog dialog, Activity activity, Consumer<String> onVenueSelected) {
        View root = dialog.findViewById(android.R.id.content);
        if (root == null) root = dialog.getWindow().getDecorView();
        MaterialToolbar toolbar = root.findViewById(R.id.venue_toolbar);
        toolbar.setNavigationOnClickListener(v -> dialog.dismiss());
        PickerController controller = new PickerController(activity, root, onVenueSelected);
        dialog.setOnDismissListener(d -> controller.onDetach());
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_venue_picker);
        View root = findViewById(android.R.id.content);
        MaterialToolbar toolbar = root.findViewById(R.id.venue_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        controller = new PickerController(this, root, value -> {
            Intent result = new Intent();
            result.putExtra(EXTRA_VENUE, value);
            setResult(Activity.RESULT_OK, result);
            finish();
        });
    }

    @Override protected void onResume() { super.onResume(); if (controller != null) controller.onResume(); }
    @Override protected void onPause() { if (controller != null) controller.onPause(); super.onPause(); }
    @Override protected void onDestroy() { if (controller != null) controller.onDetach(); super.onDestroy(); }

    private static void configureOsmdroid(Activity activity) {
        Configuration.getInstance().setUserAgentValue(PHOTON_USER_AGENT);
    }

    private static String formatLatLng(GeoPoint point) {
        return String.format(Locale.US, "%.5f, %.5f", point.getLatitude(), point.getLongitude());
    }

    /** One geocoded suggestion: a display label plus the point it resolves to. */
    private static final class PlaceResult {
        final String label;
        final double lat;
        final double lon;
        PlaceResult(String label, double lat, double lon) { this.label = label; this.lat = lat; this.lon = lon; }
    }

    private static String readStream(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toString("UTF-8");
    }

    private static String optNonEmpty(JSONObject props, String key) {
        if (props == null || props.isNull(key)) return null;
        String value = props.optString(key, null);
        return (value == null || value.trim().isEmpty()) ? null : value;
    }

    private static String formatPhotonLabel(JSONObject properties) {
        if (properties == null) return "Selected location";
        List<String> parts = new ArrayList<>();
        String housenumber = optNonEmpty(properties, "housenumber");
        String street = optNonEmpty(properties, "street");
        String name = optNonEmpty(properties, "name");
        if (street != null) parts.add(housenumber != null ? housenumber + " " + street : street);
        else if (name != null) parts.add(name);
        String city = optNonEmpty(properties, "city");
        if (city != null) parts.add(city);
        String state = optNonEmpty(properties, "state");
        if (state != null) parts.add(state);
        String country = optNonEmpty(properties, "country");
        if (country != null) parts.add(country);
        return parts.isEmpty() ? "Selected location" : String.join(", ", parts);
    }

    private static List<PlaceResult> parsePhotonResponse(String body) throws org.json.JSONException {
        List<PlaceResult> results = new ArrayList<>();
        JSONArray features = new JSONObject(body).getJSONArray("features");
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);
            JSONArray coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates");
            double lon = coordinates.getDouble(0);
            double lat = coordinates.getDouble(1);
            results.add(new PlaceResult(formatPhotonLabel(feature.optJSONObject("properties")), lat, lon));
        }
        return results;
    }

    /** Returns null on network/parse failure, distinct from a legitimate empty match list. */
    private static List<PlaceResult> searchPlaces(String query) {
        HttpURLConnection connection = null;
        try {
            String url = PHOTON_SEARCH_URL + URLEncoder.encode(query, "UTF-8");
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestProperty("User-Agent", PHOTON_USER_AGENT);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            if (connection.getResponseCode() != 200) return null;
            return parsePhotonResponse(readStream(connection.getInputStream()));
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static PlaceResult reverseGeocode(GeoPoint point) {
        HttpURLConnection connection = null;
        try {
            String url = String.format(Locale.US, PHOTON_REVERSE_URL, point.getLatitude(), point.getLongitude());
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestProperty("User-Agent", PHOTON_USER_AGENT);
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            if (connection.getResponseCode() != 200) return null;
            List<PlaceResult> results = parsePhotonResponse(readStream(connection.getInputStream()));
            return results.isEmpty() ? null : results.get(0);
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Wires the search box, results list, map tap handling, and Use button for one picker screen (activity or dialog). */
    private static final class PickerController {
        private final Activity activity;
        private final MapView map;
        private final EditText searchInput;
        private final Button useButton;
        private final View resultsCard;
        private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();
        private final Handler debounceHandler = new Handler(Looper.getMainLooper());
        private final List<String> resultLabels = new ArrayList<>();
        private final List<PlaceResult> resultPlaces = new ArrayList<>();
        private final ResultAdapter resultAdapter = new ResultAdapter();
        private Runnable pendingSearch;
        private Marker selectedMarker;
        private int searchRequestId;
        private boolean suppressWatcher;

        PickerController(Activity activity, View root, Consumer<String> onVenueSelected) {
            this.activity = activity;
            configureOsmdroid(activity);
            searchInput = root.findViewById(R.id.venue_search_input);
            useButton = root.findViewById(R.id.use_venue_button);
            map = root.findViewById(R.id.venue_map);
            map.setTileSource(VENUE_MAP_TILE_SOURCE);
            map.setMultiTouchControls(true);
            map.getController().setZoom(4.0);
            map.getController().setCenter(new GeoPoint(39.8283, -98.5795));
            map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
                @Override public boolean singleTapConfirmedHelper(GeoPoint point) { onMapTapped(point); return true; }
                @Override public boolean longPressHelper(GeoPoint point) { return false; }
            }));

            resultsCard = root.findViewById(R.id.venue_results_card);
            RecyclerView results = root.findViewById(R.id.venue_results);
            results.setLayoutManager(new LinearLayoutManager(activity));
            results.setAdapter(resultAdapter);

            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override public void afterTextChanged(Editable s) {
                    if (suppressWatcher) return;
                    if (pendingSearch != null) debounceHandler.removeCallbacks(pendingSearch);
                    String query = s.toString().trim();
                    if (query.length() > 2) {
                        pendingSearch = () -> runSearch(query, false);
                        debounceHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
                    } else {
                        clearSuggestions();
                    }
                }
            });
            searchInput.setOnEditorActionListener((v, actionId, event) -> {
                if (pendingSearch != null) debounceHandler.removeCallbacks(pendingSearch);
                runSearch(searchInput.getText().toString().trim(), true);
                return true;
            });
            useButton.setOnClickListener(v -> {
                String value = searchInput.getText().toString().trim();
                if (!value.isEmpty()) onVenueSelected.accept(value);
            });
        }

        private void onMapTapped(GeoPoint point) {
            selectLocation(point, formatLatLng(point));
            searchExecutor.execute(() -> {
                PlaceResult resolved = reverseGeocode(point);
                if (resolved == null) return;
                activity.runOnUiThread(() -> setSearchText(resolved.label));
            });
        }

        private void runSearch(String query, boolean selectTopResult) {
            if (query.isEmpty()) return;
            int requestId = ++searchRequestId;
            searchExecutor.execute(() -> {
                List<PlaceResult> found = searchPlaces(query);
                activity.runOnUiThread(() -> {
                    if (requestId != searchRequestId) return;
                    resultPlaces.clear();
                    resultLabels.clear();
                    if (found != null) for (PlaceResult p : found) { resultPlaces.add(p); resultLabels.add(p.label); }
                    resultAdapter.notifyDataSetChanged();
                    if (found == null) {
                        resultsCard.setVisibility(View.GONE);
                        if (selectTopResult) Toast.makeText(activity, "Address search is unavailable", Toast.LENGTH_SHORT).show();
                    } else if (selectTopResult) {
                        resultsCard.setVisibility(View.GONE);
                        if (found.isEmpty()) {
                            Toast.makeText(activity, "No matches found", Toast.LENGTH_SHORT).show();
                        } else {
                            PlaceResult top = found.get(0);
                            selectLocation(new GeoPoint(top.lat, top.lon), top.label);
                        }
                    } else {
                        resultsCard.setVisibility(found.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                });
            });
        }

        private void clearSuggestions() {
            resultPlaces.clear();
            resultLabels.clear();
            resultAdapter.notifyDataSetChanged();
            resultsCard.setVisibility(View.GONE);
        }

        private void selectLocation(GeoPoint point, String label) {
            resultsCard.setVisibility(View.GONE);
            if (selectedMarker != null) map.getOverlays().remove(selectedMarker);
            selectedMarker = new Marker(map);
            selectedMarker.setPosition(point);
            map.getOverlays().add(selectedMarker);
            map.getController().animateTo(point);
            map.getController().setZoom(16.0);
            map.invalidate();
            setSearchText(label);
            useButton.setEnabled(true);
        }

        private void setSearchText(String label) {
            suppressWatcher = true;
            searchInput.setText(label);
            searchInput.setSelection(label.length());
            suppressWatcher = false;
        }

        void onResume() { map.onResume(); }
        void onPause() { map.onPause(); }
        void onDetach() {
            if (pendingSearch != null) debounceHandler.removeCallbacks(pendingSearch);
            searchExecutor.shutdownNow();
            map.onDetach();
        }

        private final class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.Holder> {
            @NonNull public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_venue_result, parent, false));
            }
            public void onBindViewHolder(@NonNull Holder holder, int position) {
                holder.text.setText(resultLabels.get(position));
                holder.itemView.setOnClickListener(v -> {
                    PlaceResult p = resultPlaces.get(holder.getBindingAdapterPosition());
                    selectLocation(new GeoPoint(p.lat, p.lon), p.label);
                });
            }
            public int getItemCount() { return resultLabels.size(); }
            final class Holder extends RecyclerView.ViewHolder {
                final TextView text;
                Holder(View itemView) { super(itemView); text = itemView.findViewById(R.id.venue_result_text); }
            }
        }
    }
}
