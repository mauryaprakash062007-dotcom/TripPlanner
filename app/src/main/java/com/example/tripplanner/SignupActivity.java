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

public class SignupActivity extends AppCompatActivity {

    TextInputEditText etName, etEmail, etPassword, etConfirmPassword;
    MaterialButton btnSignup;
    TextView tvGoToLogin;

    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        sharedPreferences = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSignup = findViewById(R.id.btnSignup);
        tvGoToLogin = findViewById(R.id.tvGoToLogin);

        btnSignup.setOnClickListener(v -> attemptSignup());

        tvGoToLogin.setOnClickListener(v -> {
            finish(); // Go back to login
        });
    }

    void attemptSignup() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name is required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("Email is required");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Enter a valid email");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etPassword.setError("Password is required");
            return;
        }
        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            return;
        }

        // Check if email already registered
        if (sharedPreferences.getString("reg_email_" + email, null) != null) {
            Toast.makeText(this, "Email already registered. Please login.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save credentials in SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("reg_email_" + email, email);
        editor.putString("reg_password_" + email, password);
        editor.putString("reg_username_" + email, name);
        // Auto-login: save login session immediately after signup
        editor.putBoolean(LoginActivity.KEY_IS_LOGGED_IN, true);
        editor.putString(LoginActivity.KEY_EMAIL, email);
        editor.putString(LoginActivity.KEY_USERNAME, name);
        editor.apply();

        Toast.makeText(this, "Welcome, " + name + "!", Toast.LENGTH_SHORT).show();
        // Go directly to Dashboard instead of back to login
        goToDashboard();
    }

    void goToDashboard() {
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
