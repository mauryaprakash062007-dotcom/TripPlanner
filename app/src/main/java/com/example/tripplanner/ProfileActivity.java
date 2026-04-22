package com.example.tripplanner;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class ProfileActivity extends AppCompatActivity {

    TextInputEditText etName, etEmail, etPhone, etBio;
    MaterialButton btnSave, btnBack;
    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        sharedPreferences = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etBio = findViewById(R.id.etBio);
        btnSave = findViewById(R.id.btnSave);
        btnBack = findViewById(R.id.btnBack);

        // Load existing profile from SharedPreferences
        etName.setText(sharedPreferences.getString(LoginActivity.KEY_USERNAME, ""));
        etEmail.setText(sharedPreferences.getString(LoginActivity.KEY_EMAIL, ""));
        etPhone.setText(sharedPreferences.getString("profile_phone", ""));
        etBio.setText(sharedPreferences.getString("profile_bio", ""));

        btnSave.setOnClickListener(v -> saveProfile());
        btnBack.setOnClickListener(v -> finish());
    }

    void saveProfile() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        String bio = etBio.getText() != null ? etBio.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            etName.setError("Name cannot be empty");
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(LoginActivity.KEY_USERNAME, name);
        editor.putString("profile_phone", phone);
        editor.putString("profile_bio", bio);
        editor.apply();

        Toast.makeText(this, "Profile saved successfully!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
