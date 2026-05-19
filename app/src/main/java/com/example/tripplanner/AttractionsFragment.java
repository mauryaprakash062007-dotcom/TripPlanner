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
import android.widget.Toast;

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

    String destination;
    long startDate;
    List<String> selectedActivities = new ArrayList<>();

    TextView tvLoading;
    ListView listAttractions;
    ChipGroup chipGroupFilters;
    MaterialButton btnRetry;
    View btnReload;

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
        TextView tvName, tvCategory, tvAddress, tvRating;
        MaterialButton btnSelect, btnVisit, btnStar;
    }

    public static class Place {
        public String name;
        public String category;
        public String subCategory;
        public String address;
        public int priority;
        public float rating;
        public String emoji;
        public String imageUrl;
        public boolean isStarred;
    }

    public static JSONObject placeToJson(Place p) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("name", p.name);
            obj.put("category", p.category);
            obj.put("subCategory", p.subCategory);
            obj.put("address", p.address);
            obj.put("priority", p.priority);
            obj.put("rating", p.rating);
            obj.put("emoji", p.emoji);
            obj.put("imageUrl", p.imageUrl);
            obj.put("isStarred", p.isStarred);
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    public static Place jsonToPlace(JSONObject obj) {
        Place p = new Place();
        p.name = obj.optString("name");
        p.category = obj.optString("category");
        p.subCategory = obj.optString("subCategory");
        p.address = obj.optString("address");
        p.priority = obj.optInt("priority");
        p.rating = (float) obj.optDouble("rating", -1);
        p.emoji = obj.optString("emoji");
        p.imageUrl = obj.optString("imageUrl");
        p.isStarred = obj.optBoolean("isStarred", false);
        return p;
    }

    public static ArrayList<Place> getVisitedPlaces(android.content.Context context, String destination, long startDate) {
        ArrayList<Place> list = new ArrayList<>();
        if (context == null) return list;
        android.content.SharedPreferences prefs = context.getSharedPreferences(LoginActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        String key = "visited_places_" + destination + "_" + startDate;
        String jsonStr = prefs.getString(key, "[]");
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                list.add(jsonToPlace(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            // Ignore
        }
        return list;
    }

    public static void saveVisitedPlaces(android.content.Context context, String destination, long startDate, ArrayList<Place> list) {
        if (context == null) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences(LoginActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        String key = "visited_places_" + destination + "_" + startDate;
        JSONArray arr = new JSONArray();
        for (Place p : list) {
            JSONObject obj = placeToJson(p);
            if (obj != null) arr.put(obj);
        }
        prefs.edit().putString(key, arr.toString()).apply();
    }

    public static ArrayList<Place> getStarredPlaces(android.content.Context context, String destination, long startDate) {
        ArrayList<Place> list = new ArrayList<>();
        if (context == null) return list;
        android.content.SharedPreferences prefs = context.getSharedPreferences(LoginActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        String key = "starred_places_" + destination + "_" + startDate;
        String jsonStr = prefs.getString(key, "[]");
        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                list.add(jsonToPlace(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            // Ignore
        }
        return list;
    }

    public static void saveStarredPlaces(android.content.Context context, String destination, long startDate, ArrayList<Place> list) {
        if (context == null) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences(LoginActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        String key = "starred_places_" + destination + "_" + startDate;
        JSONArray arr = new JSONArray();
        for (Place p : list) {
            JSONObject obj = placeToJson(p);
            if (obj != null) arr.put(obj);
        }
        prefs.edit().putString(key, arr.toString()).apply();
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
        btnReload        = view.findViewById(R.id.btnReloadAttractions);

        if (getArguments() != null) {
            destination        = getArguments().getString("destination");
            startDate          = getArguments().getLong("startDate", 0);
            selectedActivities = getArguments().getStringArrayList("activities");
        }

        btnRetry.setOnClickListener(v -> {
            btnRetry.setVisibility(View.GONE);
            allFetchedPlaces.clear();
            seenNames.clear();
            fetchGooglePlacesData();
        });

        btnReload.setOnClickListener(v -> {
            tvLoading.setText("Finding top spots...");
            tvLoading.setVisibility(View.VISIBLE);
            listAttractions.setVisibility(View.GONE);
            allFetchedPlaces.clear();
            seenNames.clear();
            fetchGooglePlacesData();
        });

        setupFilters();
        fetchGooglePlacesData();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (tvLoading != null) applyFilter();
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
        if (getContext() == null || tvLoading == null) return;
        if (allFetchedPlaces.isEmpty()) return;
        
        List<Place> filtered;
        synchronized (allFetchedPlaces) {
            ArrayList<Place> visited = getVisitedPlaces(getContext(), destination, startDate);
            java.util.HashSet<String> visitedNames = new java.util.HashSet<>();
            for (Place vp : visited) {
                visitedNames.add(vp.name.toLowerCase());
            }

            filtered = allFetchedPlaces.stream()
                    .filter(p -> !visitedNames.contains(p.name.toLowerCase()))
                    .filter(p -> currentFilter.equals("All") || (p.subCategory != null && p.subCategory.equalsIgnoreCase(currentFilter)))
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
                "Top hotels and resorts in " + destination,
                "Best bars, pubs, and night clubs in " + destination
        };

        AtomicInteger pending = new AtomicInteger(queries.length);

        for (String q : queries) {
            performGoogleTextSearch(q, lat, lon, hasBias, pending);
        }
    }

    private String getApiKey() {
        if (getContext() == null) return "";
        android.content.SharedPreferences prefs = getContext().getSharedPreferences(LoginActivity.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        return prefs.getString("google_api_key", "");
    }

    void performGoogleTextSearch(String query, double lat, double lon, boolean hasBias, AtomicInteger pending) {
        try {
            String apiKey = getApiKey();
            if (apiKey.isEmpty()) {
                checkDone(pending);
                return;
            }

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
                    .addHeader("X-Goog-Api-Key", apiKey)
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
                String lowerName = name.toLowerCase();
                String lowerAddr = p.address.toLowerCase();

                // Map Google's robust types to our UI
                if (type.contains("beach") || lowerName.contains("beach") || lowerAddr.contains("beach")) {
                    p.category = "🏖️ Beach"; p.subCategory = "Beaches"; p.priority = 1; p.emoji = "🏖️";
                } else if (type.contains("restaurant") || type.contains("cafe") || type.contains("food") || type.contains("bakery")) {
                    if (type.contains("bar") || type.contains("night_club") || lowerName.contains(" pub") || lowerName.contains(" bar") || lowerName.contains(" club")) {
                        p.category = "🍺 Nightlife"; p.subCategory = "Nightlife"; p.priority = 5; p.emoji = "🍺";
                    } else {
                        p.category = "🍴 Food"; p.subCategory = "Food"; p.priority = 4; p.emoji = "🍴";
                    }
                } else if (type.contains("bar") || type.contains("night_club") || type.contains("tavern") || lowerName.contains(" pub") || lowerName.contains(" bar") || lowerName.contains(" club")) {
                    p.category = "🍺 Nightlife"; p.subCategory = "Nightlife"; p.priority = 5; p.emoji = "🍺";
                } else if (type.contains("hotel") || type.contains("lodging") || type.contains("resort")) {
                    p.category = "🏨 Stay"; p.subCategory = "Stay"; p.priority = 6; p.emoji = "🏨";
                } else {
                    p.category = "🏛️ Sights"; p.subCategory = "Sights"; p.priority = 2; p.emoji = "🏛️";
                }

                // If Google provides photos, construct the direct media URL
                if (pObj.has("photos")) {
                    JSONArray photos = pObj.getJSONArray("photos");
                    if (photos.length() > 0) {
                        String photoName = photos.getJSONObject(0).getString("name");
                        // Let Google redirect (302) to actual image — Glide follows redirects natively
                        p.imageUrl = "https://places.googleapis.com/v1/" + photoName 
                                + "/media?maxHeightPx=600&maxWidthPx=800&key=" + getApiKey();
                        Log.d(TAG, "Photo URL for " + p.name + ": " + p.imageUrl);
                    }
                }

                allFetchedPlaces.add(p);
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error", e);
        }
    }

    private void sortAllFetchedPlaces() {
        if (getContext() == null) return;
        
        // Read persisted stars and update the in-memory list before sorting
        ArrayList<Place> starredList = getStarredPlaces(getContext(), destination, startDate);
        java.util.HashSet<String> starredNames = new java.util.HashSet<>();
        for (Place sp : starredList) {
            starredNames.add(sp.name.toLowerCase());
        }

        synchronized (allFetchedPlaces) {
            for (Place p : allFetchedPlaces) {
                p.isStarred = starredNames.contains(p.name.toLowerCase());
            }

            allFetchedPlaces.sort((a, b) -> {
                if (a.isStarred && !b.isStarred) return -1;
                if (!a.isStarred && b.isStarred) return 1;
                return Float.compare(b.rating, a.rating);
            });
        }
    }

    void checkDone(AtomicInteger pending) {
        if (pending.decrementAndGet() == 0) {
            mainHandler.post(() -> {
                if (allFetchedPlaces.isEmpty()) {
                    showDemoData(true);
                } else {
                    sortAllFetchedPlaces();
                    applyFilter();
                }
            });
        }
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────
    void showPlaces(final ArrayList<Place> places) {
        if (getContext() == null) return;
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
                    convertView = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_attraction, parent, false);
                    h = new ViewHolder();
                    h.ivImage       = convertView.findViewById(R.id.ivAttractionImage);
                    h.tvPlaceholder = convertView.findViewById(R.id.tvAttractionImagePlaceholder);
                    h.tvName        = convertView.findViewById(R.id.tvAttractionName);
                    h.tvCategory    = convertView.findViewById(R.id.tvAttractionCategory);
                    h.tvAddress     = convertView.findViewById(R.id.tvAttractionAddress);
                    h.tvRating      = convertView.findViewById(R.id.tvAttractionRating);
                    h.btnSelect     = convertView.findViewById(R.id.btnSelect);
                    h.btnVisit      = convertView.findViewById(R.id.btnVisit);
                    h.btnStar       = convertView.findViewById(R.id.btnStar);
                    convertView.setTag(h);
                } else {
                    h = (ViewHolder) convertView.getTag();
                }

                Place place = places.get(position);
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
                    
                    // Capture views for use inside listener
                    final ImageView imgView = h.ivImage;
                    final TextView phView = h.tvPlaceholder;
                    
                    Glide.with(parent.getContext())
                            .load(place.imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .transition(DrawableTransitionOptions.withCrossFade(250))
                            .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e,
                                        Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                        boolean isFirstResource) {
                                    Log.e(TAG, "Image load failed for: " + place.name + " url: " + place.imageUrl, e);
                                    // Show emoji placeholder on failure
                                    imgView.setVisibility(View.GONE);
                                    phView.setVisibility(View.VISIBLE);
                                    return true; // handled
                                }
                                @Override
                                public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                                        com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                        com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                    return false; // let Glide handle display
                                }
                            })
                            .into(h.ivImage);
                } else {
                    // Google will occasionally miss an image, fallback to emoji
                    h.ivImage.setVisibility(View.GONE);
                    h.tvPlaceholder.setVisibility(View.VISIBLE);
                }

                h.btnSelect.setOnClickListener(v -> openMapsNavigation(place));

                if (place.isStarred) {
                    h.btnStar.setIconResource(R.drawable.ic_star_filled);
                } else {
                    h.btnStar.setIconResource(R.drawable.ic_star_border);
                }
                h.btnStar.setOnClickListener(v -> {
                    place.isStarred = !place.isStarred;
                    // Toggle icon instantly
                    if (place.isStarred) {
                        h.btnStar.setIconResource(R.drawable.ic_star_filled);
                    } else {
                        h.btnStar.setIconResource(R.drawable.ic_star_border);
                    }
                    
                    // Persist starred state to SharedPreferences
                    if (getContext() != null) {
                        ArrayList<Place> starredList = getStarredPlaces(getContext(), destination, startDate);
                        if (place.isStarred) {
                            boolean found = false;
                            for (Place sp : starredList) {
                                if (sp.name.equalsIgnoreCase(place.name)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                starredList.add(place);
                                saveStarredPlaces(getContext(), destination, startDate, starredList);
                            }
                        } else {
                            starredList.removeIf(sp -> sp.name.equalsIgnoreCase(place.name));
                            saveStarredPlaces(getContext(), destination, startDate, starredList);
                        }
                    }
                    
                    Toast.makeText(getContext(), place.isStarred ? "Starred!" : "Unstarred!", Toast.LENGTH_SHORT).show();
                });

                h.btnVisit.setText("Visited");
                h.btnVisit.setOnClickListener(v -> {
                    if (getContext() == null) return;
                    ArrayList<Place> visited = getVisitedPlaces(getContext(), destination, startDate);
                    boolean already = false;
                    for (Place vp : visited) {
                        if (vp.name.equalsIgnoreCase(place.name)) {
                            already = true;
                            break;
                        }
                    }
                    if (!already) {
                        visited.add(place);
                        saveVisitedPlaces(getContext(), destination, startDate, visited);
                    }
                    applyFilter();
                    Toast.makeText(getContext(), "Marked as Visited!", Toast.LENGTH_SHORT).show();
                });
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
        sortAllFetchedPlaces();
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