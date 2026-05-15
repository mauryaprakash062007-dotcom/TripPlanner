package com.example.tripplanner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;

public class DashboardActivity extends AppCompatActivity {

    TextView tvWelcome, tvLastDestination, tvLastDate;
    MaterialButton btnNewTrip, btnLogout;
    CardView cardPlanTrip, cardMyProfile, cardBudget, cardTipOfDay, cardTripHistory;

    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        sharedPreferences = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);

        tvWelcome = findViewById(R.id.tvWelcome);
        tvLastDestination = findViewById(R.id.tvLastDestination);
        tvLastDate = findViewById(R.id.tvLastDate);
        btnNewTrip = findViewById(R.id.btnNewTrip);
        btnLogout = findViewById(R.id.btnLogout);
        cardPlanTrip = findViewById(R.id.cardPlanTrip);
        cardMyProfile = findViewById(R.id.cardMyProfile);
        cardBudget = findViewById(R.id.cardBudget);
        cardTipOfDay = findViewById(R.id.cardTipOfDay);
        cardTripHistory = findViewById(R.id.cardTripHistory);

        String username = sharedPreferences.getString(LoginActivity.KEY_USERNAME, "Traveler");
        tvWelcome.setText("Hello, " + username + "! ✈️");

        loadLastTrip();

        btnNewTrip.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        cardPlanTrip.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        cardMyProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        cardBudget.setOnClickListener(v -> startActivity(new Intent(this, BudgetActivity.class)));
        cardTipOfDay.setOnClickListener(v -> startActivity(new Intent(this, TravelTipsActivity.class)));
        cardTripHistory.setOnClickListener(v -> startActivity(new Intent(this, TripHistoryActivity.class)));
        btnLogout.setOnClickListener(v -> logout());
    }

    void loadLastTrip() {
        String lastDest = sharedPreferences.getString("last_destination", null);
        String lastDate = sharedPreferences.getString("last_trip_date", null);
        if (lastDest != null) {
            tvLastDestination.setText("Last trip: " + lastDest);
            tvLastDate.setText(lastDate != null ? lastDate : "");
        } else {
            tvLastDestination.setText("No trips planned yet");
            tvLastDate.setText("Plan your first trip!");
        }
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
