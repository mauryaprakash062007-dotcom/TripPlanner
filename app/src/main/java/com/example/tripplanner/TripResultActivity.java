package com.example.tripplanner;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class TripResultActivity extends AppCompatActivity {

    String destination;
    long startDate, endDate;
    ArrayList<String> activities;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_result);

        // Get data from intent
        destination = getIntent().getStringExtra("destination");
        startDate = getIntent().getLongExtra("startDate", 0);
        endDate = getIntent().getLongExtra("endDate", 0);
        activities = getIntent().getStringArrayListExtra("activities");

        if (activities == null) activities = new ArrayList<>();

        // Set toolbar
        TextView tvTitle = findViewById(R.id.tvToolbarTitle);
        TextView tvSubtitle = findViewById(R.id.tvToolbarSubtitle);
        tvTitle.setText("✈️ " + destination);

        // Build subtitle string with date range and days
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM", Locale.getDefault());
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        start.setTimeInMillis(startDate);
        end.setTimeInMillis(endDate);

        long diffMs = endDate - startDate;
        long numDays = (diffMs / (1000 * 60 * 60 * 24)) + 1;

        tvSubtitle.setText(fmt.format(start.getTime()) + " – " + fmt.format(end.getTime()) + "  •  " + numDays + " days");

        // Setup ViewPager with tabs
        ViewPager2 viewPager = findViewById(R.id.viewPager);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        TripPagerAdapter adapter = new TripPagerAdapter(this, destination, startDate, endDate, activities);
        viewPager.setAdapter(adapter);

        String[] tabTitles = {"🌤 Weather", "🏛 Attractions", "🧳 Pack List"};

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(tabTitles[position]);
        }).attach();
    }
}
