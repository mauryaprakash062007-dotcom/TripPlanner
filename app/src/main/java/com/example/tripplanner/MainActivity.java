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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    android.widget.EditText etDestination;
    TextView tvStartDate, tvEndDate, tvDaysCount;
    ChipGroup chipGroupActivities;
    MaterialButton btnPlanTrip;
    android.view.View btnStartDate, btnEndDate;

    Calendar startCalendar = null;
    Calendar endCalendar = null;

    SimpleDateFormat displayFormat = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());

    DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new DatabaseHelper(this);

        etDestination = findViewById(R.id.etDestination);
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
