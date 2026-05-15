package com.example.tripplanner;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripHistoryActivity extends AppCompatActivity {

    ListView listTrips;
    TextView tvEmpty;
    MaterialButton btnBack;
    DatabaseHelper db;
    List<DatabaseHelper.TripRecord> trips;
    SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_history);

        db = new DatabaseHelper(this);
        listTrips = findViewById(R.id.listTripHistory);
        tvEmpty = findViewById(R.id.tvNoTrips);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        loadTrips();

        listTrips.setOnItemLongClickListener((parent, view, position, id) -> {
            DatabaseHelper.TripRecord trip = trips.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("Delete trip?")
                    .setMessage("Remove " + trip.destination + " from history?")
                    .setPositiveButton("Delete", (d, w) -> {
                        db.deleteTrip(trip.id);
                        Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                        loadTrips();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        listTrips.setOnItemClickListener((parent, view, position, id) -> {
            DatabaseHelper.TripRecord trip = trips.get(position);
            Intent intent = new Intent(this, TripResultActivity.class);
            intent.putExtra("destination", trip.destination);
            intent.putExtra("startDate", trip.startDate);
            intent.putExtra("endDate", trip.endDate);
            ArrayList<String> acts = new ArrayList<>();
            if (trip.activities != null && !trip.activities.isEmpty()) {
                for (String a : trip.activities.split(",")) acts.add(a.trim());
            }
            intent.putStringArrayListExtra("activities", acts);
            startActivity(intent);
        });
    }

    void loadTrips() {
        trips = db.getAllTrips();
        if (trips.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            listTrips.setVisibility(View.GONE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);
        listTrips.setVisibility(View.VISIBLE);

        ArrayAdapter<DatabaseHelper.TripRecord> adapter = new ArrayAdapter<DatabaseHelper.TripRecord>(
                this, R.layout.item_trip_history, trips) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_trip_history, parent, false);
                }
                DatabaseHelper.TripRecord t = getItem(position);
                TextView tvDest = convertView.findViewById(R.id.tvHistoryDestination);
                TextView tvDates = convertView.findViewById(R.id.tvHistoryDates);
                TextView tvActs = convertView.findViewById(R.id.tvHistoryActivities);

                tvDest.setText("✈️ " + t.destination);
                tvDates.setText(fmt.format(new Date(t.startDate)) + " → " + fmt.format(new Date(t.endDate)));
                tvActs.setText(t.activities != null && !t.activities.isEmpty() ? t.activities : "No activities");
                return convertView;
            }
        };
        listTrips.setAdapter(adapter);
    }
}
