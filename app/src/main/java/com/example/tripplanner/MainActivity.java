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
            String encoded = URLEncoder.encode(query, "UTF-8");
            String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + encoded + "&count=5&language=en&format=json";

            Request request = new Request.Builder().url(url).build();
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {}

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) return;
                    try {
                        String body = response.body().string();
                        JSONObject root = new JSONObject(body);
                        JSONArray results = root.optJSONArray("results");
                        ArrayList<String> suggestions = new ArrayList<>();
                        if (results != null) {
                            for (int i = 0; i < results.length(); i++) {
                                JSONObject loc = results.getJSONObject(i);
                                String name = loc.optString("name");
                                String admin1 = loc.optString("admin1", "");
                                String country = loc.optString("country", "");

                                StringBuilder sb = new StringBuilder(name);
                                if (!admin1.isEmpty() && !admin1.equals(name)) {
                                    sb.append(", ").append(admin1);
                                }
                                if (!country.isEmpty()) {
                                    sb.append(", ").append(country);
                                }
                                suggestions.add(sb.toString());
                            }
                        }

                        mainHandler.post(() -> {
                            if (isFinishing() || isDestroyed()) return;

                            // Swap data in the existing adapter (never replace the adapter itself)
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
            // Ignore encoding errors
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
        SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString("last_destination", destination)
                .putString("last_trip_date", displayFormat.format(startCalendar.getTime()) + " → " + displayFormat.format(endCalendar.getTime()))
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
