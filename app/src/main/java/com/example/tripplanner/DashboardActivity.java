package com.example.tripplanner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;

public class DashboardActivity extends AppCompatActivity {

    TextView tvWelcome, tvLastDestination, tvLastDate, tvDaysRemaining, tvBudgetInfo;
    MaterialButton btnNewTrip, btnLogout;
    CardView cardPlanTrip, cardMyProfile, cardBudget, cardTipOfDay, cardTripHistory, cardCurrentTrip;

    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        sharedPreferences = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);

        tvWelcome = findViewById(R.id.tvWelcome);
        tvLastDestination = findViewById(R.id.tvLastDestination);
        tvLastDate = findViewById(R.id.tvLastDate);
        tvDaysRemaining = findViewById(R.id.tvDaysRemaining);
        tvBudgetInfo = findViewById(R.id.tvBudgetInfo);
        btnNewTrip = findViewById(R.id.btnNewTrip);
        btnLogout = findViewById(R.id.btnLogout);
        cardPlanTrip = findViewById(R.id.cardPlanTrip);
        cardMyProfile = findViewById(R.id.cardMyProfile);
        cardBudget = findViewById(R.id.cardBudget);
        cardTipOfDay = findViewById(R.id.cardTipOfDay);
        cardTripHistory = findViewById(R.id.cardTripHistory);
        cardCurrentTrip = findViewById(R.id.cardCurrentTrip);

        String username = sharedPreferences.getString(LoginActivity.KEY_USERNAME, "Traveler");
        tvWelcome.setText("Hello, " + username + "! ✈️");

        loadLastTrip();

        btnNewTrip.setOnClickListener(v -> handleNewTrip());
        cardPlanTrip.setOnClickListener(v -> handleNewTrip());
        cardCurrentTrip.setOnClickListener(v -> {
            if (sharedPreferences.getBoolean("has_active_plan", false)) {
                startActivity(new Intent(this, MainActivity.class));
            }
        });
        cardMyProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        cardBudget.setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
        cardTipOfDay.setOnClickListener(v -> startActivity(new Intent(this, TravelTipsActivity.class)));
        cardTripHistory.setOnClickListener(v -> startActivity(new Intent(this, TripHistoryActivity.class)));
        btnLogout.setOnClickListener(v -> logout());
    }

    void handleNewTrip() {
        if (sharedPreferences.getBoolean("has_active_plan", false)) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("New Trip")
                .setMessage("You already have an active trip. Do you want to plan a new one?")
                .setPositiveButton("Yes", (d, w) -> {
                    sharedPreferences.edit().putBoolean("has_active_plan", false).apply();
                    startActivity(new Intent(this, MainActivity.class));
                })
                .setNegativeButton("No", null)
                .show();
        } else {
            startActivity(new Intent(this, MainActivity.class));
        }
    }

    void loadLastTrip() {
        boolean hasActive = sharedPreferences.getBoolean("has_active_plan", false);
        if (hasActive) {
            String lastDest = sharedPreferences.getString("last_destination", "");
            String lastDate = sharedPreferences.getString("last_trip_date", "");
            tvLastDestination.setText("Current trip: " + lastDest);
            tvLastDate.setText(lastDate);

            // 1. Days remaining calculation
            long startDate = sharedPreferences.getLong("last_start_date", 0);
            long endDate = sharedPreferences.getLong("last_end_date", 0);
            
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long todayMs = cal.getTimeInMillis();

            // Normalize start date to midnight
            java.util.Calendar startCal = java.util.Calendar.getInstance();
            startCal.setTimeInMillis(startDate);
            startCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            startCal.set(java.util.Calendar.MINUTE, 0);
            startCal.set(java.util.Calendar.SECOND, 0);
            startCal.set(java.util.Calendar.MILLISECOND, 0);
            long normalizedStart = startCal.getTimeInMillis();

            // Normalize end date to midnight
            java.util.Calendar endCal = java.util.Calendar.getInstance();
            endCal.setTimeInMillis(endDate);
            endCal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            endCal.set(java.util.Calendar.MINUTE, 0);
            endCal.set(java.util.Calendar.SECOND, 0);
            endCal.set(java.util.Calendar.MILLISECOND, 0);
            long normalizedEnd = endCal.getTimeInMillis();

            long diffStart = normalizedStart - todayMs;
            long diffEnd = normalizedEnd - todayMs;

            long daysUntilStart = diffStart / (1000 * 60 * 60 * 24);
            long daysUntilEnd = diffEnd / (1000 * 60 * 60 * 24);

            tvDaysRemaining.setVisibility(android.view.View.VISIBLE);
            if (todayMs < normalizedStart) {
                tvDaysRemaining.setText("⏳ Starts in " + daysUntilStart + " day" + (daysUntilStart == 1 ? "" : "s"));
                tvDaysRemaining.setTextColor(getResources().getColor(R.color.accent_blue, null));
            } else if (todayMs <= normalizedEnd) {
                long daysRemaining = daysUntilEnd + 1;
                tvDaysRemaining.setText("🔥 " + daysRemaining + " day" + (daysRemaining == 1 ? "" : "s") + " remaining");
                tvDaysRemaining.setTextColor(getResources().getColor(R.color.accent_teal, null));
            } else {
                tvDaysRemaining.setText("✅ Trip completed");
                tvDaysRemaining.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
            }

            // 2. Budget calculation
            try {
                String tripPrefix = lastDest + "_" + startDate + "_";
                String totalStr = sharedPreferences.getString(tripPrefix + "budget_total", "0");
                double total = totalStr.isEmpty() ? 0 : Double.parseDouble(totalStr);

                double accommodation = getBudgetVal(tripPrefix + "budget_accommodation");
                double food = getBudgetVal(tripPrefix + "budget_food");
                double transport = getBudgetVal(tripPrefix + "budget_transport");
                double activities = getBudgetVal(tripPrefix + "budget_activities");
                double spent = accommodation + food + transport + activities;

                tvBudgetInfo.setVisibility(android.view.View.VISIBLE);
                tvBudgetInfo.setText(String.format("💰 Budget: ₹%.2f | Spent: ₹%.2f", total, spent));
            } catch (Exception e) {
                tvBudgetInfo.setVisibility(android.view.View.GONE);
            }
        } else {
            tvLastDestination.setText("No active trip");
            tvLastDate.setText("Plan a new trip!");
            tvDaysRemaining.setVisibility(android.view.View.GONE);
            tvBudgetInfo.setVisibility(android.view.View.GONE);
        }
    }

    private double getBudgetVal(String key) {
        String val = sharedPreferences.getString(key, "0");
        return val.isEmpty() ? 0 : Double.parseDouble(val);
    }

    void logout() {
        sharedPreferences.edit().putBoolean(LoginActivity.KEY_IS_LOGGED_IN, false).apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadLastTrip();
    }
}
