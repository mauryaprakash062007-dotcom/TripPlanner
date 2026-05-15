package com.example.tripplanner;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class BudgetActivity extends AppCompatActivity {

    TextInputEditText etTotalBudget, etAccommodation, etFood, etTransport, etActivities;
    TextView tvSpent, tvRemaining;
    MaterialButton btnCalculate, btnSave, btnBack;
    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_budget);

        sharedPreferences = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);

        etTotalBudget = findViewById(R.id.etTotalBudget);
        etAccommodation = findViewById(R.id.etAccommodation);
        etFood = findViewById(R.id.etFood);
        etTransport = findViewById(R.id.etTransport);
        etActivities = findViewById(R.id.etActivities);
        tvSpent = findViewById(R.id.tvSpent);
        tvRemaining = findViewById(R.id.tvRemaining);
        btnCalculate = findViewById(R.id.btnCalculate);
        btnSave = findViewById(R.id.btnSave);
        btnBack = findViewById(R.id.btnBack);

        // Load saved budget from SharedPreferences
        etTotalBudget.setText(sharedPreferences.getString("budget_total", ""));
        etAccommodation.setText(sharedPreferences.getString("budget_accommodation", ""));
        etFood.setText(sharedPreferences.getString("budget_food", ""));
        etTransport.setText(sharedPreferences.getString("budget_transport", ""));
        etActivities.setText(sharedPreferences.getString("budget_activities", ""));

        calculateBudget(); // Show initial calculation

        btnCalculate.setOnClickListener(v -> calculateBudget());
        btnSave.setOnClickListener(v -> saveBudget());
        btnBack.setOnClickListener(v -> finish());
    }

    void calculateBudget() {
        double total = parseDouble(etTotalBudget);
        double accommodation = parseDouble(etAccommodation);
        double food = parseDouble(etFood);
        double transport = parseDouble(etTransport);
        double activities = parseDouble(etActivities);

        double spent = accommodation + food + transport + activities;
        double remaining = total - spent;

        tvSpent.setText(String.format("₹%.2f spent", spent));
        if (remaining >= 0) {
            tvRemaining.setText(String.format("₹%.2f remaining", remaining));
            tvRemaining.setTextColor(getResources().getColor(R.color.accent_teal, null));
        } else {
            tvRemaining.setText(String.format("₹%.2f over budget!", Math.abs(remaining)));
            tvRemaining.setTextColor(getResources().getColor(android.R.color.holo_red_light, null));
        }
    }

    void saveBudget() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("budget_total", getStr(etTotalBudget));
        editor.putString("budget_accommodation", getStr(etAccommodation));
        editor.putString("budget_food", getStr(etFood));
        editor.putString("budget_transport", getStr(etTransport));
        editor.putString("budget_activities", getStr(etActivities));
        editor.apply();

        calculateBudget();
        Toast.makeText(this, "Budget saved!", Toast.LENGTH_SHORT).show();
    }

    double parseDouble(TextInputEditText et) {
        try {
            String text = et.getText() != null ? et.getText().toString().trim() : "";
            return text.isEmpty() ? 0 : Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    String getStr(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
