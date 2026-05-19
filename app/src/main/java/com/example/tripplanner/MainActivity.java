package com.example.tripplanner;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    android.widget.AutoCompleteTextView etDestination;
    TextView tvStartDate, tvEndDate, tvDaysCount;
    ChipGroup chipGroupActivities;
    MaterialButton btnPlanTrip;
    android.view.View btnStartDate, btnEndDate;

    Calendar startCalendar = null;
    Calendar endCalendar = null;

    SimpleDateFormat displayFormat = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());

    DatabaseHelper db;
    
    OkHttpClient httpClient = new OkHttpClient();
    android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    Runnable searchRunnable;
    boolean blockNextTextChange = false;
    android.widget.ArrayAdapter<String> suggestionAdapter;
    List<String> suggestionsList = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean("has_active_plan", false)) {
            String dest = prefs.getString("last_destination", "");
            long start = prefs.getLong("last_start_date", 0);
            long end = prefs.getLong("last_end_date", 0);
            java.util.Set<String> activitiesSet = prefs.getStringSet("last_activities", new java.util.HashSet<>());
            ArrayList<String> activities = new ArrayList<>(activitiesSet);

            Intent intent = new Intent(this, TripResultActivity.class);
            intent.putExtra("destination", dest);
            intent.putExtra("startDate", start);
            intent.putExtra("endDate", end);
            intent.putStringArrayListExtra("activities", activities);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        db = new DatabaseHelper(this);

        etDestination = findViewById(R.id.etDestination);

        // Create a persistent adapter with a no-op filter + convertResultToString
        // that strips "City, State, Country" down to just "City".
        // This is critical because AutoCompleteTextView calls replaceText() AFTER
        // onItemClick, and replaceText uses convertResultToString to decide what
        // text to insert. If we don't override it, ACTV overwrites our setText().
        suggestionAdapter = new android.widget.ArrayAdapter<String>(
                this, android.R.layout.simple_dropdown_item_1line, suggestionsList) {
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override protected FilterResults performFiltering(CharSequence c) {
                        FilterResults r = new FilterResults();
                        r.values = suggestionsList;
                        r.count = suggestionsList.size();
                        return r;
                    }
                    @Override protected void publishResults(CharSequence c, FilterResults r) {
                        notifyDataSetChanged();
                    }
                    // Return full string so "Chennai, Tamil Nadu, India" stays in the field
                    @Override public CharSequence convertResultToString(Object resultValue) {
                        return (String) resultValue;
                    }
                };
            }
        };
        etDestination.setAdapter(suggestionAdapter);
        etDestination.setThreshold(1);

        tvStartDate = findViewById(R.id.tvStartDate);
        tvEndDate = findViewById(R.id.tvEndDate);
        tvDaysCount = findViewById(R.id.tvDaysCount);
        chipGroupActivities = findViewById(R.id.chipGroupActivities);
        btnPlanTrip = findViewById(R.id.btnPlanTrip);
        btnStartDate = findViewById(R.id.btnStartDate);
        btnEndDate = findViewById(R.id.btnEndDate);

        btnStartDate.setOnClickListener(v -> showDatePicker(true));
        btnEndDate.setOnClickListener(v -> showDatePicker(false));
        btnPlanTrip.setOnClickListener(v -> planTrip());

        // When user taps a suggestion — just block the next text change to prevent re-search
        etDestination.setOnItemClickListener((parent, view, position, id) -> {
            blockNextTextChange = true;
            etDestination.dismissDropDown();
        });

        etDestination.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (blockNextTextChange) {
                    blockNextTextChange = false;
                    return;
                }

                if (searchRunnable != null) {
                    mainHandler.removeCallbacks(searchRunnable);
                }

                String query = s.toString().trim();
                if (query.length() >= 2) {
                    searchRunnable = () -> fetchSuggestions(query);
                    mainHandler.postDelayed(searchRunnable, 350);
                }
            }
        });
    }

    void fetchSuggestions(String query) {
        try {
            SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
            String apiKey = prefs.getString("google_api_key", "");
            if (apiKey.isEmpty()) return;

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("input", query);
            JSONArray types = new JSONArray();
            types.put("(cities)");
            jsonBody.put("includedPrimaryTypes", types);

            okhttp3.RequestBody reqBody = okhttp3.RequestBody.create(
                    jsonBody.toString(), okhttp3.MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url("https://places.googleapis.com/v1/places:autocomplete")
                    .post(reqBody)
                    .addHeader("X-Goog-Api-Key", apiKey)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {}

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) return;
                    try {
                        String body = response.body().string();
                        JSONObject root = new JSONObject(body);
                        JSONArray suggestionsJson = root.optJSONArray("suggestions");
                        ArrayList<String> suggestions = new ArrayList<>();
                        if (suggestionsJson != null) {
                            for (int i = 0; i < suggestionsJson.length(); i++) {
                                JSONObject sug = suggestionsJson.getJSONObject(i);
                                JSONObject pred = sug.optJSONObject("placePrediction");
                                if (pred != null) {
                                    JSONObject textObj = pred.optJSONObject("text");
                                    if (textObj != null) {
                                        String desc = textObj.optString("text");
                                        suggestions.add(desc);
                                    }
                                }
                            }
                        }

                        mainHandler.post(() -> {
                            if (isFinishing() || isDestroyed()) return;

                            // Swap data in the existing adapter
                            suggestionsList.clear();
                            suggestionsList.addAll(suggestions);
                            suggestionAdapter.notifyDataSetChanged();

                            if (!suggestions.isEmpty() && etDestination.hasFocus()
                                    && etDestination.getText().length() >= 2) {
                                etDestination.showDropDown();
                            }
                        });
                    } catch (Exception e) {
                        // Ignore parse errors
                    }
                }
            });
        } catch (Exception e) {
            // Ignore json/network errors
        }
    }

    void showDatePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, day);
                    if (isStart) {
                        startCalendar = selected;
                        tvStartDate.setText(displayFormat.format(selected.getTime()));
                    } else {
                        endCalendar = selected;
                        tvEndDate.setText(displayFormat.format(selected.getTime()));
                    }
                    updateDaysCount();
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));

        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    void updateDaysCount() {
        if (startCalendar != null && endCalendar != null) {
            long diff = endCalendar.getTimeInMillis() - startCalendar.getTimeInMillis();
            long days = diff / (1000 * 60 * 60 * 24);
            if (days < 0) {
                tvDaysCount.setText("⚠️ End date must be after start date");
                tvDaysCount.setTextColor(getResources().getColor(android.R.color.holo_red_light, null));
            } else {
                tvDaysCount.setText("✅ " + (days + 1) + " day trip");
                tvDaysCount.setTextColor(getResources().getColor(R.color.accent_teal, null));
            }
        }
    }

    ArrayList<String> getSelectedActivities() {
        ArrayList<String> selected = new ArrayList<>();
        int[] chipIds = {
                R.id.chipBeach, R.id.chipSightseeing, R.id.chipRestaurant,
                R.id.chipCafe, R.id.chipClubs, R.id.chipPubs,
                R.id.chipStay, R.id.chipShopping, R.id.chipHiking,
                R.id.chipCamping, R.id.chipAdventure
        };
        for (int id : chipIds) {
            Chip chip = findViewById(id);
            if (chip != null && chip.isChecked()) {
                String text = chip.getText().toString();
                // Strip emoji and take the main word
                String clean = text.replaceAll("[^a-zA-Z /]", "").trim();
                selected.add(clean);
            }
        }
        return selected;
    }

    void planTrip() {
        SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        String apiKey = prefs.getString("google_api_key", "");
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Please set Google Places API Key in Profile settings first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, ProfileActivity.class));
            return;
        }

        String destination = etDestination.getText().toString().trim();

        if (destination.isEmpty()) {
            Toast.makeText(this, "Please enter a destination", Toast.LENGTH_SHORT).show();
            return;
        }
        if (startCalendar == null || endCalendar == null) {
            Toast.makeText(this, "Please select travel dates", Toast.LENGTH_SHORT).show();
            return;
        }
        long diff = endCalendar.getTimeInMillis() - startCalendar.getTimeInMillis();
        long days = diff / (1000 * 60 * 60 * 24);
        if (days < 0) {
            Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> activities = getSelectedActivities();

        // Save to SharedPreferences (last trip)
        java.util.Set<String> activitiesSet = new java.util.HashSet<>(activities);
        prefs.edit()
                .putString("last_destination", destination)
                .putString("last_trip_date", displayFormat.format(startCalendar.getTime()) + " → " + displayFormat.format(endCalendar.getTime()))
                .putBoolean("has_active_plan", true)
                .putLong("last_start_date", startCalendar.getTimeInMillis())
                .putLong("last_end_date", endCalendar.getTimeInMillis())
                .putStringSet("last_activities", activitiesSet)
                .apply();

        // Save to SQLite history
        String activitiesStr = String.join(", ", activities);
        db.insertTrip(destination, startCalendar.getTimeInMillis(), endCalendar.getTimeInMillis(), activitiesStr);

        Intent intent = new Intent(this, TripResultActivity.class);
        intent.putExtra("destination", destination);
        intent.putExtra("startDate", startCalendar.getTimeInMillis());
        intent.putExtra("endDate", endCalendar.getTimeInMillis());
        intent.putStringArrayListExtra("activities", activities);
        startActivity(intent);
    }
}
