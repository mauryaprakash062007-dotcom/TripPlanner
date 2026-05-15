package com.example.tripplanner;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "TripPlannerPrefs";
    public static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_EMAIL = "email";

    TextInputEditText etEmail, etPassword;
    MaterialButton btnLogin;
    TextView tvGoToSignup;

    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Auto-login if already logged in
        if (sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)) {
            goToDashboard();
            return;
        }

        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvGoToSignup = findViewById(R.id.tvGoToSignup);

        btnLogin.setOnClickListener(v -> attemptLogin());

        tvGoToSignup.setOnClickListener(v -> {
            startActivity(new Intent(this, SignupActivity.class));
        });
    }

    void attemptLogin() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email");
            return;
        }

        // Check credentials against SharedPreferences (stored during signup)
        String savedEmail = sharedPreferences.getString("reg_email_" + email, null);
        String savedPassword = sharedPreferences.getString("reg_password_" + email, null);
        String savedUsername = sharedPreferences.getString("reg_username_" + email, "Traveler");

        if (savedEmail == null) {
            Toast.makeText(this, "Account not found. Please sign up first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(savedPassword)) {
            Toast.makeText(this, "Incorrect password.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save login session in SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_USERNAME, savedUsername);
        editor.apply();

        Toast.makeText(this, "Welcome back, " + savedUsername + "!", Toast.LENGTH_SHORT).show();
        goToDashboard();
    }

    void goToDashboard() {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
