package com.example.tripplanner;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AttractionsFragment extends Fragment {

    private static final String TAG = "AttractionsFragment";

    // 3 Overpass mirrors — app tries each one until one works
    private static final String[] OVERPASS_MIRRORS = {
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass-api.de/api/interpreter",
            "https://overpass.openstreetmap.ru/api/interpreter"
    };

    String destination;
    List<String> selectedActivities = new ArrayList<>();

    TextView tvLoading;
    ListView listAttractions;
    ChipGroup chipGroupFilters;

    // Increased timeouts so Overpass has time to respond
    OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    Handler mainHandler = new Handler(Looper.getMainLooper());

    List<Place> allFetchedPlaces = new ArrayList<>();
    String currentFilter = "All";

    static class Place {
        String name;
        String category;
        String subCategory;
        String address;
        int priority;
        float rating;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_attractions, container, false);
        tvLoading = view.findViewById(R.id.tvAttractionsLoading);
        listAttractions = view.findViewById(R.id.listAttractions);
        chipGroupFilters = view.findViewById(R.id.chipGroupFilters);

        if (getArguments() != null) {
            destination = getArguments().getString("destination");
            selectedActivities = getArguments().getStringArrayList("activities");
        }

        setupFilters();
        geocodeAndFetch();
        return view;
    }

    private void setupFilters() {
        if (selectedActivities != null && !selectedActivities.isEmpty()) {
            String first = selectedActivities.get(0).toLowerCase();
            if (first.contains("beach")) {
                currentFilter = "Beaches";
                chipGroupFilters.check(R.id.chipBeaches);
            } else if (first.contains("restaurant") || first.contains("cafe") || first.contains("food")) {
                currentFilter = "Food";
                chipGroupFilters.check(R.id.chipFood);
            } else if (first.contains("club") || first.contains("pub")) {
                currentFilter = "Nightlife";
                chipGroupFilters.check(R.id.chipNightlife);
            } else if (first.contains("stay") || first.contains("rooms")) {
                currentFilter = "Stay";
                chipGroupFilters.check(R.id.chipStay);
            } else if (first.contains("sightseeing")) {
                currentFilter = "Sights";
                chipGroupFilters.check(R.id.chipSights);
            }
        }

        chipGroupFilters.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipAll) currentFilter = "All";
            else if (checkedId == R.id.chipSights) currentFilter = "Sights";
            else if (checkedId == R.id.chipFood) currentFilter = "Food";
            else if (checkedId == R.id.chipNightlife) currentFilter = "Nightlife";
            else if (checkedId == R.id.chipStay) currentFilter = "Stay";
            else if (checkedId == R.id.chipBeaches) currentFilter = "Beaches";
            applyFilter();
        });
    }

    private void applyFilter() {
        if (allFetchedPlaces.isEmpty()) return;

        List<Place> filtered;
        if (currentFilter.equals("All")) {
            filtered = new ArrayList<>(allFetchedPlaces);
        } else {
            filtered = allFetchedPlaces.stream()
                    .filter(p -> p.subCategory != null && p.subCategory.equalsIgnoreCase(currentFilter))
                    .collect(Collectors.toList());
        }

        if (filtered.isEmpty()) {
            tvLoading.setText("No " + currentFilter + " found for " + destination);
            tvLoading.setVisibility(View.VISIBLE);
            listAttractions.setVisibility(View.GONE);
        } else {
            tvLoading.setVisibility(View.GONE);
            listAttractions.setVisibility(View.VISIBLE);
            showPlaces(new ArrayList<>(filtered));
        }
    }

    void geocodeAndFetch() {
        tvLoading.setText("Finding places in " + destination + "...");
        tvLoading.setVisibility(View.VISIBLE);
        listAttractions.setVisibility(View.GONE);

        try {
            String encoded = URLEncoder.encode(destination, "UTF-8");
            String geoUrl = "https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=json&limit=1";

            Request geoRequest = new Request.Builder()
                    .url(geoUrl)
                    .addHeader("User-Agent", "TripPlannerApp/1.0")
                    .build();

            httpClient.newCall(geoRequest).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Geocode failed: " + e.getMessage());
                    mainHandler.post(() -> showDemoData());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        JSONArray results = new JSONArray(body);
                        if (results.length() > 0) {
                            JSONObject loc = results.getJSONObject(0);
                            double lat = loc.getDouble("lat");
                            double lon = loc.getDouble("lon");
                            Log.d(TAG, "Geocoded " + destination + " -> " + lat + "," + lon);
                            fetchWithMirror(lat, lon, 0);
                        } else {
                            Log.e(TAG, "Geocode returned no results for: " + destination);
                            mainHandler.post(() -> showDemoData());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Geocode parse error: " + e.getMessage());
                        mainHandler.post(() -> showDemoData());
                    }
                }
            });
        } catch (Exception e) {
            showDemoData();
        }
    }

    void fetchWithMirror(double lat, double lon, int mirrorIndex) {
        if (mirrorIndex >= OVERPASS_MIRRORS.length) {
            Log.e(TAG, "All Overpass mirrors failed, showing demo data");
            mainHandler.post(this::showDemoData);
            return;
        }

        String q = "[out:json][timeout:60];"
                + "("
                // Sights
                + "node(around:15000," + lat + "," + lon + ")[\"tourism\"~\"attraction|museum|viewpoint|gallery|zoo|theme_park|artwork\"];"
                + "way(around:15000," + lat + "," + lon + ")[\"tourism\"~\"attraction|museum|viewpoint|gallery|zoo|theme_park\"];"
                + "node(around:15000," + lat + "," + lon + ")[\"historic\"~\"monument|castle|fort|palace|ruins|temple|church|mosque\"];"
                + "way(around:15000," + lat + "," + lon + ")[\"historic\"~\"monument|castle|fort|palace|ruins|temple|church|mosque\"];"
                + "node(around:15000," + lat + "," + lon + ")[\"amenity\"~\"place_of_worship|cinema|theatre|library\"];"
                // Food
                + "node(around:8000," + lat + "," + lon + ")[\"amenity\"~\"restaurant|cafe|fast_food|food_court|bakery|ice_cream\"];"
                + "way(around:8000," + lat + "," + lon + ")[\"amenity\"~\"restaurant|cafe|fast_food|food_court\"];"
                // Nightlife
                + "node(around:8000," + lat + "," + lon + ")[\"amenity\"~\"nightclub|pub|bar|brewery\"];"
                + "way(around:8000," + lat + "," + lon + ")[\"amenity\"~\"nightclub|pub|bar\"];"
                // Stay
                + "node(around:8000," + lat + "," + lon + ")[\"tourism\"~\"hotel|hostel|guest_house|motel|resort\"];"
                + "way(around:8000," + lat + "," + lon + ")[\"tourism\"~\"hotel|hostel|guest_house|motel|resort\"];"
                // Beaches — ways + relations because Indian beaches are polygons not points
                + "node(around:30000," + lat + "," + lon + ")[\"natural\"=\"beach\"];"
                + "way(around:30000," + lat + "," + lon + ")[\"natural\"=\"beach\"];"
                + "relation(around:30000," + lat + "," + lon + ")[\"natural\"=\"beach\"];"
                + "node(around:30000," + lat + "," + lon + ")[\"leisure\"=\"beach_resort\"];"
                + "way(around:30000," + lat + "," + lon + ")[\"leisure\"=\"beach_resort\"];"
                + ");"
                + "out tags center 500;";

        String url;
        try {
            url = OVERPASS_MIRRORS[mirrorIndex] + "?data=" + URLEncoder.encode(q, "UTF-8");
        } catch (Exception e) {
            fetchWithMirror(lat, lon, mirrorIndex + 1);
            return;
        }

        Log.d(TAG, "Trying Overpass mirror " + mirrorIndex + ": " + OVERPASS_MIRRORS[mirrorIndex]);
        mainHandler.post(() -> tvLoading.setText("Loading places... (attempt " + (mirrorIndex + 1) + ")"));

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "TripPlannerApp/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Mirror " + mirrorIndex + " failed: " + e.getMessage());
                fetchWithMirror(lat, lon, mirrorIndex + 1);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Mirror " + mirrorIndex + " HTTP error: " + response.code());
                    fetchWithMirror(lat, lon, mirrorIndex + 1);
                    return;
                }
                String body = response.body().string();
                if (body.length() < 50 || !body.contains("elements")) {
                    Log.e(TAG, "Mirror " + mirrorIndex + " bad response, length=" + body.length());
                    fetchWithMirror(lat, lon, mirrorIndex + 1);
                    return;
                }
                Log.d(TAG, "Mirror " + mirrorIndex + " OK, size=" + body.length());
                mainHandler.post(() -> parseOverpassAndShow(body));
            }
        });
    }

    void parseOverpassAndShow(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray elements = root.optJSONArray("elements");
            if (elements == null || elements.length() == 0) {
                Log.e(TAG, "Overpass returned 0 elements");
                showDemoData();
                return;
            }

            Log.d(TAG, "Overpass returned " + elements.length() + " elements");

            LinkedHashMap<String, Place> seen = new LinkedHashMap<>();

            for (int i = 0; i < elements.length(); i++) {
                JSONObject el = elements.getJSONObject(i);
                JSONObject tags = el.optJSONObject("tags");
                if (tags == null) continue;

                String name = tags.optString("name", "").trim();
                if (name.isEmpty()) name = tags.optString("name:en", "").trim();
                if (name.isEmpty()) name = tags.optString("official_name", "").trim();
                if (name.isEmpty()) continue;

                String key = name.toLowerCase(Locale.getDefault());
                if (seen.containsKey(key)) continue;

                Place place = new Place();
                place.name = name;
                place.rating = 3.5f + (float)(Math.random() * 1.4f);

                String tourism  = tags.optString("tourism",  "");
                String historic = tags.optString("historic", "");
                String amenity  = tags.optString("amenity",  "");
                String natural  = tags.optString("natural",  "");
                String leisure  = tags.optString("leisure",  "");

                if (natural.equals("beach") || leisure.equals("beach_resort") || tourism.equals("beach")) {
                    place.category = "🏖️ Beach";
                    place.subCategory = "Beaches";
                    place.priority = 1;
                } else if (amenity.equals("restaurant") || amenity.equals("cafe")
                        || amenity.equals("fast_food") || amenity.equals("food_court")
                        || amenity.equals("bakery") || amenity.equals("ice_cream")) {
                    String icon = amenity.equals("cafe") ? "☕" : amenity.equals("bakery") ? "🥐" : "🍴";
                    place.category = icon + " " + formatCategory(amenity);
                    place.subCategory = "Food";
                    place.priority = 4;
                } else if (amenity.equals("nightclub") || amenity.equals("pub")
                        || amenity.equals("bar") || amenity.equals("brewery")) {
                    String icon = amenity.equals("nightclub") ? "💃" : "🍺";
                    place.category = icon + " " + formatCategory(amenity);
                    place.subCategory = "Nightlife";
                    place.priority = 5;
                } else if (tourism.equals("hotel") || tourism.equals("hostel")
                        || tourism.equals("guest_house") || tourism.equals("motel")
                        || tourism.equals("resort")) {
                    place.category = "🏨 " + formatCategory(tourism);
                    place.subCategory = "Stay";
                    place.priority = 6;
                } else {
                    String subType = !tourism.isEmpty() ? tourism
                            : !historic.isEmpty() ? historic
                            : !amenity.isEmpty() ? amenity
                            : "Attraction";
                    String icon = !historic.isEmpty() ? "🗿" : "🏛️";
                    place.category = icon + " " + formatCategory(subType);
                    place.subCategory = "Sights";
                    place.priority = 2;
                }

                String street = tags.optString("addr:street", "");
                String city   = tags.optString("addr:city", "");
                String suburb = tags.optString("addr:suburb", "");
                if (!street.isEmpty() && !city.isEmpty()) {
                    place.address = street + ", " + city;
                } else if (!suburb.isEmpty()) {
                    place.address = suburb + ", " + destination;
                } else if (!city.isEmpty()) {
                    place.address = city;
                } else {
                    place.address = destination;
                }

                seen.put(key, place);
            }

            allFetchedPlaces = new ArrayList<>(seen.values());
            allFetchedPlaces.sort((a, b) -> Integer.compare(a.priority, b.priority));

            Log.d(TAG, "Parsed " + allFetchedPlaces.size() + " unique places");

            if (allFetchedPlaces.isEmpty()) showDemoData();
            else applyFilter();

        } catch (Exception e) {
            Log.e(TAG, "Parse error: " + e.getMessage());
            showDemoData();
        }
    }

    String formatCategory(String raw) {
        if (raw == null || raw.isEmpty()) return "Place";
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            if (!part.isEmpty())
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    void showDemoData() {
        ArrayList<Place> list = new ArrayList<>();
        add(list, destination + " Central Beach",   "🏖️ Beach",      "Beaches",   "Coastline, " + destination,        1, 4.5f);
        add(list, destination + " Heritage Museum", "🏛️ Museum",     "Sights",    "Old Town, " + destination,         2, 4.2f);
        add(list, destination + " Fort",            "🗿 Fort",        "Sights",    "City Center, " + destination,     2, 4.1f);
        add(list, destination + " Local Kitchen",   "🍴 Restaurant", "Food",      "Main Street, " + destination,     4, 4.3f);
        add(list, destination + " Seafood Corner",  "🍴 Restaurant", "Food",      "Beach Road, " + destination,      4, 4.6f);
        add(list, destination + " Café",            "☕ Cafe",        "Food",      "MG Road, " + destination,         4, 4.0f);
        add(list, destination + " Sports Bar",      "🍺 Bar",         "Nightlife", "City Center, " + destination,    5, 3.9f);
        add(list, destination + " Grand Hotel",     "🏨 Hotel",       "Stay",      "Station Road, " + destination,   6, 4.4f);
        add(list, destination + " Beach Resort",    "🏨 Resort",      "Stay",      "Coastal Road, " + destination,   6, 4.7f);
        allFetchedPlaces = list;
        applyFilter();
    }

    void add(ArrayList<Place> list, String name, String cat, String subCat, String addr, int prio, float rating) {
        Place p = new Place();
        p.name = name; p.category = cat; p.subCategory = subCat;
        p.address = addr; p.priority = prio; p.rating = rating;
        list.add(p);
    }

    void showPlaces(ArrayList<Place> places) {
        tvLoading.setVisibility(View.GONE);
        listAttractions.setVisibility(View.VISIBLE);

        ArrayAdapter<Place> adapter = new ArrayAdapter<Place>(requireContext(),
                R.layout.item_attraction, places) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_attraction, parent, false);
                }
                Place place = getItem(position);
                ((TextView) convertView.findViewById(R.id.tvAttractionNumber)).setText(String.valueOf(position + 1));
                ((TextView) convertView.findViewById(R.id.tvAttractionName)).setText(place.name);
                ((TextView) convertView.findViewById(R.id.tvAttractionCategory)).setText(place.category);
                ((TextView) convertView.findViewById(R.id.tvAttractionAddress)).setText(place.address);

                TextView ratingView = convertView.findViewById(R.id.tvAttractionRating);
                if (ratingView != null) {
                    ratingView.setText(String.format(Locale.getDefault(), "★ %.1f", place.rating));
                }

                return convertView;
            }
        };
        listAttractions.setAdapter(adapter);
    }
}
