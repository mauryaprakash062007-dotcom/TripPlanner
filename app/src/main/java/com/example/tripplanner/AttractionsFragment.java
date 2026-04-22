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
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AttractionsFragment extends Fragment {

    private static final String TAG = "AttractionsFragment";
    String destination;
    List<String> selectedActivities = new ArrayList<>();

    TextView tvLoading;
    ListView listAttractions;
    ChipGroup chipGroupFilters;

    OkHttpClient httpClient = new OkHttpClient();
    Handler mainHandler = new Handler(Looper.getMainLooper());
    
    List<Place> allFetchedPlaces = new ArrayList<>();
    String currentFilter = "All";

    static class Place {
        String name;
        String category;
        String subCategory; // Sights, Food, Nightlife, Stay, Beaches
        String address;
        int priority;
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
        if (allFetchedPlaces.isEmpty()) {
            return;
        }

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
        try {
            // Use Nominatim for better geocoding results
            String encoded = URLEncoder.encode(destination, "UTF-8");
            String geoUrl = "https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=json&limit=1";

            Request geoRequest = new Request.Builder()
                    .url(geoUrl)
                    .addHeader("User-Agent", "TripPlannerApp/1.0")
                    .build();

            httpClient.newCall(geoRequest).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
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
                            String displayName = loc.optString("display_name", destination);
                            mainHandler.post(() -> fetchOverpassAttractions(lat, lon, displayName));
                        } else {
                            mainHandler.post(() -> showDemoData());
                        }
                    } catch (Exception e) {
                        mainHandler.post(() -> showDemoData());
                    }
                }
            });
        } catch (Exception e) {
            showDemoData();
        }
    }

    void fetchOverpassAttractions(double lat, double lon, String fullAddress) {
        // Querying Overpass with specific filters for high accuracy
        String q = "[out:json][timeout:30];"
            + "("
            // Tourism/Sights
            + "node(around:10000," + lat + "," + lon + ")[\"tourism\"~\"attraction|museum|viewpoint|gallery|zoo|theme_park\"];"
            + "node(around:10000," + lat + "," + lon + ")[\"historic\"~\"monument|castle|fort|palace\"];"
            // Food & Drink
            + "node(around:5000," + lat + "," + lon + ")[\"amenity\"~\"restaurant|cafe|fast_food\"];"
            // Nightlife
            + "node(around:5000," + lat + "," + lon + ")[\"amenity\"~\"nightclub|pub|bar\"];"
            // Stay
            + "node(around:5000," + lat + "," + lon + ")[\"tourism\"~\"hotel|hostel|guest_house\"];"
            // Beaches
            + "node(around:20000," + lat + "," + lon + ")[\"natural\"=\"beach\"];"
            + "way(around:20000," + lat + "," + lon + ")[\"natural\"=\"beach\"];"
            + ");"
            + "out tags center 100;";

        String url;
        try {
            url = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q, "UTF-8");
        } catch (Exception e) {
            showDemoData();
            return;
        }

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "TripPlannerApp/1.0")
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> showDemoData());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> showDemoData());
                    return;
                }
                String body = response.body().string();
                mainHandler.post(() -> parseOverpassAndShow(body));
            }
        });
    }

    void parseOverpassAndShow(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray elements = root.optJSONArray("elements");
            if (elements == null || elements.length() == 0) {
                showDemoData();
                return;
            }

            LinkedHashMap<String, Place> seen = new LinkedHashMap<>();

            for (int i = 0; i < elements.length(); i++) {
                JSONObject el = elements.getJSONObject(i);
                JSONObject tags = el.optJSONObject("tags");
                if (tags == null) continue;

                String name = tags.optString("name", "").trim();
                if (name.isEmpty()) name = tags.optString("name:en", "").trim();
                if (name.isEmpty()) continue;

                String key = name.toLowerCase(Locale.getDefault());
                if (seen.containsKey(key)) continue;

                Place place = new Place();
                place.name = name;

                String tourism  = tags.optString("tourism",  "");
                String historic = tags.optString("historic", "");
                String amenity  = tags.optString("amenity",  "");
                String natural  = tags.optString("natural",  "");
                
                // Categorization
                if (natural.equals("beach")) {
                    place.category = "🏖️ Beach";
                    place.subCategory = "Beaches";
                    place.priority = 1;
                } else if (amenity.equals("restaurant") || amenity.equals("cafe") || amenity.equals("fast_food")) {
                    place.category = (amenity.equals("cafe") ? "☕ " : "🍴 ") + formatCategory(amenity);
                    place.subCategory = "Food";
                    place.priority = 4;
                } else if (amenity.equals("nightclub") || amenity.equals("pub") || amenity.equals("bar")) {
                    place.category = (amenity.equals("nightclub") ? "💃 " : "🍺 ") + formatCategory(amenity);
                    place.subCategory = "Nightlife";
                    place.priority = 5;
                } else if (tourism.equals("hotel") || tourism.equals("hostel") || tourism.equals("guest_house")) {
                    place.category = "🏨 " + formatCategory(tourism);
                    place.subCategory = "Stay";
                    place.priority = 6;
                } else {
                    String sub = !tourism.isEmpty() ? tourism : (!historic.isEmpty() ? historic : "Sights");
                    place.category = "🏛️ " + formatCategory(sub);
                    place.subCategory = "Sights";
                    place.priority = 2;
                }

                String addr = tags.optString("addr:street", "");
                if (addr.isEmpty()) addr = tags.optString("addr:city", destination);
                place.address = addr;

                seen.put(key, place);
            }

            allFetchedPlaces = new ArrayList<>(seen.values());
            allFetchedPlaces.sort((a, b) -> Integer.compare(a.priority, b.priority));

            if (allFetchedPlaces.isEmpty()) showDemoData();
            else applyFilter();

        } catch (Exception e) {
            Log.e(TAG, "Error parsing JSON", e);
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
        // Fallback demo data only if API fails completely
        ArrayList<Place> list = new ArrayList<>();
        add(list, "🏖️ " + destination + " Main Beach", "Beach", "Beaches", "Coastline", 1);
        add(list, "🍴 " + destination + " Local Kitchen", "Restaurant", "Food", "Main St", 4);
        add(list, "🏨 " + destination + " Grand Hotel", "Hotel", "Stay", "City Center", 6);
        add(list, "🏛️ " + destination + " Heritage Museum", "Museum", "Sights", "Old Town", 2);
        
        allFetchedPlaces = list;
        applyFilter();
    }

    void add(ArrayList<Place> list, String name, String cat, String subCat, String addr, int prio) {
        Place p = new Place(); p.name=name; p.category=cat; p.subCategory=subCat; p.address=addr; p.priority=prio; list.add(p);
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
                return convertView;
            }
        };
        listAttractions.setAdapter(adapter);
    }
}
