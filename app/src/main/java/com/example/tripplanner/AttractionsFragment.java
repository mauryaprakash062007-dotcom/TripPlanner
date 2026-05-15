package com.example.tripplanner;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AttractionsFragment extends Fragment {

    private static final String TAG = "AttractionsFragment";

    // API key is now stored in local.properties and injected via BuildConfig
    private static final String GOOGLE_API_KEY = BuildConfig.GOOGLE_API_KEY;

    String destination;
    List<String> selectedActivities = new ArrayList<>();

    TextView tvLoading;
    ListView listAttractions;
    ChipGroup chipGroupFilters;
    MaterialButton btnRetry;

    OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    Handler mainHandler = new Handler(Looper.getMainLooper());

    List<Place> allFetchedPlaces = Collections.synchronizedList(new ArrayList<>());
    Set<String> seenNames = Collections.synchronizedSet(new HashSet<>());
    String currentFilter = "All";

    static class ViewHolder {
        ImageView ivImage;
        TextView tvPlaceholder;
        TextView tvNumber, tvName, tvCategory, tvAddress, tvRating;
        MaterialButton btnSelect;
    }

    static class Place {
        String name;
        String category;
        String subCategory;
        String address;
        int priority;
        float rating;
        String emoji;
        String imageUrl;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_attractions, container, false);
        tvLoading        = view.findViewById(R.id.tvAttractionsLoading);
        listAttractions  = view.findViewById(R.id.listAttractions);
        chipGroupFilters = view.findViewById(R.id.chipGroupFilters);
        btnRetry         = view.findViewById(R.id.btnRetryAttractions);

        if (getArguments() != null) {
            destination        = getArguments().getString("destination");
            selectedActivities = getArguments().getStringArrayList("activities");
        }

        btnRetry.setOnClickListener(v -> {
            btnRetry.setVisibility(View.GONE);
            allFetchedPlaces.clear();
            seenNames.clear();
            fetchGooglePlacesData();
        });

        setupFilters();
        fetchGooglePlacesData();
        return view;
    }

    // ─── Filters ──────────────────────────────────────────────────────────────
    private void setupFilters() {
        if (selectedActivities != null && !selectedActivities.isEmpty()) {
            String first = selectedActivities.get(0).toLowerCase();
            if      (first.contains("beach"))                                                       { currentFilter = "Beaches";   chipGroupFilters.check(R.id.chipBeaches); }
            else if (first.contains("restaurant")||first.contains("cafe")||first.contains("food"))  { currentFilter = "Food";      chipGroupFilters.check(R.id.chipFood); }
            else if (first.contains("club")||first.contains("pub"))                                 { currentFilter = "Nightlife"; chipGroupFilters.check(R.id.chipNightlife); }
            else if (first.contains("stay")||first.contains("rooms"))                               { currentFilter = "Stay";      chipGroupFilters.check(R.id.chipStay); }
            else if (first.contains("sightseeing"))                                                 { currentFilter = "Sights";    chipGroupFilters.check(R.id.chipSights); }
        }

        chipGroupFilters.setOnCheckedChangeListener((group, checkedId) -> {
            if      (checkedId == R.id.chipAll)       currentFilter = "All";
            else if (checkedId == R.id.chipSights)    currentFilter = "Sights";
            else if (checkedId == R.id.chipFood)      currentFilter = "Food";
            else if (checkedId == R.id.chipNightlife) currentFilter = "Nightlife";
            else if (checkedId == R.id.chipStay)      currentFilter = "Stay";
            else if (checkedId == R.id.chipBeaches)   currentFilter = "Beaches";
            applyFilter();
        });
    }

    private void applyFilter() {
        if (allFetchedPlaces.isEmpty()) return;
        
        List<Place> filtered;
        synchronized (allFetchedPlaces) {
            filtered = currentFilter.equals("All")
                    ? new ArrayList<>(allFetchedPlaces)
                    : allFetchedPlaces.stream()
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

    // ─── Google Places API (New) ──────────────────────────────────────────────
    void fetchGooglePlacesData() {
        tvLoading.setText("Finding top places in " + destination + "...");
        tvLoading.setVisibility(View.VISIBLE);
        listAttractions.setVisibility(View.GONE);

        // Step 1: Geocode destination to get lat/lon for location bias
        String cityOnly = destination.contains(",") ? destination.split(",")[0].trim() : destination;
        try {
            String encoded = URLEncoder.encode(cityOnly, "UTF-8");
            Request geoReq = new Request.Builder()
                    .url("https://geocoding-api.open-meteo.com/v1/search?name=" + encoded + "&count=1&language=en&format=json")
                    .build();

            httpClient.newCall(geoReq).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    // Fallback: search without bias
                    launchGoogleSearches(0, 0, false);
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try {
                        JSONObject root = new JSONObject(response.body().string());
                        JSONArray results = root.optJSONArray("results");
                        if (results != null && results.length() > 0) {
                            JSONObject first = results.getJSONObject(0);
                            launchGoogleSearches(first.getDouble("latitude"), first.getDouble("longitude"), true);
                        } else {
                            launchGoogleSearches(0, 0, false);
                        }
                    } catch (Exception e) {
                        launchGoogleSearches(0, 0, false);
                    }
                }
            });
        } catch (Exception e) {
            launchGoogleSearches(0, 0, false);
        }
    }

    void launchGoogleSearches(double lat, double lon, boolean hasBias) {
        String[] queries = {
                "Top tourist attractions and landmarks in " + destination,
                "Best restaurants, cafes, and bakeries in " + destination,
                "Top hotels, resorts, and nightlife in " + destination
        };

        AtomicInteger pending = new AtomicInteger(queries.length);

        for (String q : queries) {
            performGoogleTextSearch(q, lat, lon, hasBias, pending);
        }
    }

    void performGoogleTextSearch(String query, double lat, double lon, boolean hasBias, AtomicInteger pending) {
        try {
            JSONObject body = new JSONObject();
            body.put("textQuery", query);
            body.put("languageCode", "en");
            body.put("maxResultCount", 15);

            // Bias results to destination city (10km radius) so we don't get random
            // results from user's IP location
            if (hasBias) {
                JSONObject center = new JSONObject();
                center.put("latitude", lat);
                center.put("longitude", lon);
                JSONObject circle = new JSONObject();
                circle.put("center", center);
                circle.put("radius", 10000.0);
                JSONObject locationBias = new JSONObject();
                locationBias.put("circle", circle);
                body.put("locationBias", locationBias);
            }

            RequestBody reqBody = RequestBody.create(
                    body.toString(), MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url("https://places.googleapis.com/v1/places:searchText")
                    .post(reqBody)
                    .addHeader("X-Goog-Api-Key", GOOGLE_API_KEY)
                    // Requesting exactly the fields we need (including photos)
                    .addHeader("X-Goog-FieldMask", "places.displayName.text,places.formattedAddress,places.rating,places.primaryType,places.photos")
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    checkDone(pending);
                }
                @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        parseGooglePlacesResponse(response.body().string());
                    } else {
                        Log.e(TAG, "Google API Error: " + response.code() + " " + response.message());
                    }
                    checkDone(pending);
                }
            });
        } catch (Exception e) {
            checkDone(pending);
        }
    }

    void parseGooglePlacesResponse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray places = root.optJSONArray("places");
            if (places == null) return;

            for (int i = 0; i < places.length(); i++) {
                JSONObject pObj = places.getJSONObject(i);
                String name = pObj.getJSONObject("displayName").getString("text");
                
                // Prevent duplicates (e.g. a hotel showing up in sights and hotels)
                if (seenNames.contains(name.toLowerCase())) continue;
                seenNames.add(name.toLowerCase());

                Place p = new Place();
                p.name = name;
                p.address = pObj.optString("formattedAddress", destination);
                p.rating = (float) pObj.optDouble("rating", -1.0);

                String type = pObj.optString("primaryType", "tourist_attraction").toLowerCase();

                // Map Google's robust types to our UI
                if (type.contains("restaurant") || type.contains("cafe") || type.contains("food") || type.contains("bakery")) {
                    p.category = "🍴 Food"; p.subCategory = "Food"; p.priority = 4; p.emoji = "🍴";
                } else if (type.contains("bar") || type.contains("night_club")) {
                    p.category = "🍺 Nightlife"; p.subCategory = "Nightlife"; p.priority = 5; p.emoji = "🍺";
                } else if (type.contains("hotel") || type.contains("lodging") || type.contains("resort")) {
                    p.category = "🏨 Stay"; p.subCategory = "Stay"; p.priority = 6; p.emoji = "🏨";
                } else if (type.contains("beach")) {
                    p.category = "🏖️ Beach"; p.subCategory = "Beaches"; p.priority = 1; p.emoji = "🏖️";
                } else {
                    p.category = "🏛️ Sights"; p.subCategory = "Sights"; p.priority = 2; p.emoji = "🏛️";
                }

                // If Google provides photos, construct the direct media URL
                if (pObj.has("photos")) {
                    JSONArray photos = pObj.getJSONArray("photos");
                    if (photos.length() > 0) {
                        String photoName = photos.getJSONObject(0).getString("name");
                        // Use max dimensions to get a high-quality but compressed image
                        p.imageUrl = "https://places.googleapis.com/v1/" + photoName 
                                + "/media?maxHeightPx=600&maxWidthPx=800&key=" + GOOGLE_API_KEY;
                    }
                }

                allFetchedPlaces.add(p);
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error", e);
        }
    }

    void checkDone(AtomicInteger pending) {
        if (pending.decrementAndGet() == 0) {
            mainHandler.post(() -> {
                if (allFetchedPlaces.isEmpty()) {
                    showDemoData(true);
                } else {
                    // Sort all collected places by rating (highest first)
                    synchronized (allFetchedPlaces) {
                        allFetchedPlaces.sort((a, b) -> Float.compare(b.rating, a.rating));
                    }
                    applyFilter();
                }
            });
        }
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────
    void showPlaces(final ArrayList<Place> places) {
        tvLoading.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);
        listAttractions.setVisibility(View.VISIBLE);

        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
            @Override public int getCount()          { return places.size(); }
            @Override public Object getItem(int pos) { return places.get(pos); }
            @Override public long getItemId(int pos) { return pos; }

            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                ViewHolder h;
                if (convertView == null) {
                    convertView = LayoutInflater.from(requireContext())
                            .inflate(R.layout.item_attraction, parent, false);
                    h = new ViewHolder();
                    h.ivImage       = convertView.findViewById(R.id.ivAttractionImage);
                    h.tvPlaceholder = convertView.findViewById(R.id.tvAttractionImagePlaceholder);
                    h.tvNumber      = convertView.findViewById(R.id.tvAttractionNumber);
                    h.tvName        = convertView.findViewById(R.id.tvAttractionName);
                    h.tvCategory    = convertView.findViewById(R.id.tvAttractionCategory);
                    h.tvAddress     = convertView.findViewById(R.id.tvAttractionAddress);
                    h.tvRating      = convertView.findViewById(R.id.tvAttractionRating);
                    h.btnSelect     = convertView.findViewById(R.id.btnSelect);
                    convertView.setTag(h);
                } else {
                    h = (ViewHolder) convertView.getTag();
                }

                Place place = places.get(position);
                h.tvNumber.setText(String.valueOf(position + 1));
                h.tvName.setText(place.name);
                h.tvCategory.setText(place.category);
                h.tvAddress.setText(place.address);
                if (place.rating >= 0) {
                    h.tvRating.setText(String.format(Locale.getDefault(), "★ %.1f", place.rating));
                    h.tvRating.setVisibility(View.VISIBLE);
                } else {
                    h.tvRating.setVisibility(View.GONE);
                }
                
                h.tvPlaceholder.setText(place.emoji != null ? place.emoji : "📍");

                if (place.imageUrl != null && !place.imageUrl.isEmpty()) {
                    h.ivImage.setVisibility(View.VISIBLE);
                    h.tvPlaceholder.setVisibility(View.GONE);
                    Glide.with(requireContext())
                            .load(place.imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .transition(DrawableTransitionOptions.withCrossFade(250))
                            .placeholder(android.R.color.transparent)
                            .error(android.R.color.transparent)
                            .into(h.ivImage);
                } else {
                    // Google will occasionally miss an image, fallback to emoji
                    h.ivImage.setVisibility(View.GONE);
                    h.tvPlaceholder.setVisibility(View.VISIBLE);
                }

                h.btnSelect.setOnClickListener(v -> openMapsNavigation(place));
                return convertView;
            }
        };

        listAttractions.setAdapter(adapter);
    }

    // ─── Google Maps navigation ───────────────────────────────────────────────
    private void openMapsNavigation(Place place) {
        try {
            String query = URLEncoder.encode(place.name + " " + place.address, "UTF-8");
            Uri mapsUri = Uri.parse("google.navigation:q=" + query + "&mode=d");
            Intent intent = new Intent(Intent.ACTION_VIEW, mapsUri);
            intent.setPackage("com.google.android.apps.maps");

            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Uri webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + query);
                startActivity(new Intent(Intent.ACTION_VIEW, webUri));
            }
        } catch (Exception e) {
            Log.e(TAG, "Maps intent failed: " + e.getMessage());
        }
    }

    // ─── Demo Data Fallback ───────────────────────────────────────────────────
    void showDemoData(boolean isOffline) {
        String lower = destination.toLowerCase();
        boolean isBeachDest = lower.contains("goa") || lower.contains("beach") || lower.contains("coast") || lower.contains("island");
        boolean isMountain = lower.contains("mountain") || lower.contains("himalaya") || lower.contains("manali") || lower.contains("trek");

        ArrayList<Place> list = new ArrayList<>();

        if (isOffline) {
            mainHandler.post(() -> {
                tvLoading.setText("⚠️ Invalid API Key or No Internet. Showing demo data.");
                tvLoading.setVisibility(View.VISIBLE);
                btnRetry.setVisibility(View.VISIBLE);
            });
        }

        if (isBeachDest) {
            addDemo(list, destination + " Main Beach",      "🏖️ Beach",      "Beaches",   "Coastline", 1, 4.5f, "🏖️");
            addDemo(list, destination + " Beach Resort",    "🏨 Resort",      "Stay",      "Beach Road", 6, 4.2f, "🏨");
            addDemo(list, destination + " Seafood Market",  "🍴 Restaurant", "Food",      "Harbor", 4, 4.8f, "🍴");
            addDemo(list, destination + " Beach Bar",       "🍺 Bar",         "Nightlife", "Beachfront", 5, 4.1f, "🍺");
        } else if (isMountain) {
            addDemo(list, destination + " Trek Point",      "🏔️ Mountain",   "Sights",    "Highlands", 1, 4.9f, "🏔️");
            addDemo(list, destination + " Base Camp",       "🏨 Lodge",       "Stay",      "Valley", 6, 4.0f, "🏨");
            addDemo(list, destination + " Mountain Cafe",   "☕ Cafe",        "Food",      "Village", 4, 4.6f, "☕");
            addDemo(list, destination + " Viewpoint",       "🏛️ Viewpoint",  "Sights",    "Summit Area", 2, 4.8f, "🏛️");
        } else {
            addDemo(list, destination + " Central Square",  "🏛️ Monument",   "Sights",    "City Center", 2, 4.7f, "🏛️");
            addDemo(list, destination + " Fine Dining",     "🍴 Restaurant", "Food",      "Downtown", 4, 4.5f, "🍴");
            addDemo(list, destination + " Grand Hotel",     "🏨 Hotel",       "Stay",      "Business District", 6, 4.3f, "🏨");
            addDemo(list, destination + " Nightclub",       "💃 Club",        "Nightlife", "Entertainment Zone", 5, 4.2f, "💃");
        }

        allFetchedPlaces = Collections.synchronizedList(list);
        applyFilter();
    }

    void addDemo(ArrayList<Place> list, String name, String cat, String subCat,
                 String addr, int prio, float rating, String emoji) {
        Place p = new Place();
        p.name = name; p.category = cat; p.subCategory = subCat;
        p.address = addr + ", " + destination; p.priority = prio; p.rating = rating; p.emoji = emoji;
        list.add(p);
    }
}