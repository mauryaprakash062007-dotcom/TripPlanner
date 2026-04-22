package com.example.tripplanner;

import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PackListFragment extends Fragment {

    String destination;
    long startDate, endDate;
    ArrayList<String> activities;

    ListView listPackItems;
    ProgressBar packProgressBar;
    TextView tvPackProgress;

    OkHttpClient httpClient = new OkHttpClient();
    Handler mainHandler = new Handler(Looper.getMainLooper());

    // Destination intelligence flags
    boolean isBeachDest = false;
    boolean isMountainDest = false;
    boolean isDesertDest = false;
    boolean isColdDest = false;
    boolean isCityDest = false;
    boolean isReligiousDest = false;

    static class PackItem {
        String name;
        String reason;
        boolean checked = false;

        PackItem(String name, String reason) {
            this.name = name;
            this.reason = reason;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_packlist, container, false);

        listPackItems = view.findViewById(R.id.listPackItems);
        packProgressBar = view.findViewById(R.id.packProgressBar);
        tvPackProgress = view.findViewById(R.id.tvPackProgress);

        if (getArguments() != null) {
            destination = getArguments().getString("destination");
            startDate = getArguments().getLong("startDate");
            endDate = getArguments().getLong("endDate");
            activities = getArguments().getStringArrayList("activities");
        }

        if (activities == null) activities = new ArrayList<>();

        // Step 1: Detect destination type via geocoding + name analysis
        analyzeDestination();
        return view;
    }

    // ── Analyze destination: geocode to get country/region info + keyword check ──
    void analyzeDestination() {
        // First do keyword analysis on the destination name itself
        analyzeByKeywords();

        // Then geocode to get more info (country, region)
        try {
            String encoded = URLEncoder.encode(destination, "UTF-8");
            String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + encoded + "&count=1&language=en&format=json";

            Request request = new Request.Builder().url(url).build();
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    mainHandler.post(() -> fetchWeatherForPacking(0, 0, false));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String body = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> parseGeoAndAnalyze(body));
                }
            });
        } catch (Exception e) {
            fetchWeatherForPacking(0, 0, false);
        }
    }

    void analyzeByKeywords() {
        String dest = destination.toLowerCase(Locale.getDefault());

        // Beach / coastal keywords
        if (dest.contains("beach") || dest.contains("goa") || dest.contains("maldives") ||
                dest.contains("phuket") || dest.contains("bali") || dest.contains("hawaii") ||
                dest.contains("miami") || dest.contains("cancun") || dest.contains("ibiza") ||
                dest.contains("coast") || dest.contains("island") || dest.contains("bay") ||
                dest.contains("sea") || dest.contains("ocean") || dest.contains("shore") ||
                dest.contains("lakshadweep") || dest.contains("andaman") || dest.contains("pondicherry") ||
                dest.contains("varkala") || dest.contains("kovalam") || dest.contains("gokarna")) {
            isBeachDest = true;
        }

        // Mountain / hill keywords
        if (dest.contains("mountain") || dest.contains("hill") || dest.contains("manali") ||
                dest.contains("shimla") || dest.contains("darjeeling") || dest.contains("ooty") ||
                dest.contains("coorg") || dest.contains("munnar") || dest.contains("leh") ||
                dest.contains("ladakh") || dest.contains("spiti") || dest.contains("himachal") ||
                dest.contains("uttarakhand") || dest.contains("alps") || dest.contains("himalaya") ||
                dest.contains("trek") || dest.contains("peak") || dest.contains("valley") ||
                dest.contains("mussoorie") || dest.contains("nainital") || dest.contains("kodaikanal") ||
                dest.contains("swiss") || dest.contains("queenstown") || dest.contains("interlaken")) {
            isMountainDest = true;
        }

        // Desert / hot dry keywords
        if (dest.contains("desert") || dest.contains("rajasthan") || dest.contains("jaisalmer") ||
                dest.contains("jodhpur") || dest.contains("dubai") || dest.contains("abu dhabi") ||
                dest.contains("cairo") || dest.contains("marrakech") || dest.contains("sahara") ||
                dest.contains("arizona") || dest.contains("nevada") || dest.contains("las vegas") ||
                dest.contains("bikaner") || dest.contains("barmer")) {
            isDesertDest = true;
        }

        // Cold / snow keywords
        if (dest.contains("iceland") || dest.contains("norway") || dest.contains("finland") ||
                dest.contains("alaska") || dest.contains("canada") || dest.contains("siberia") ||
                dest.contains("antarctica") || dest.contains("leh") || dest.contains("ladakh") ||
                dest.contains("spiti") || dest.contains("sweden") || dest.contains("denmark") ||
                dest.contains("scotland") || dest.contains("greenland")) {
            isColdDest = true;
        }

        // Major city / urban keywords
        if (dest.contains("delhi") || dest.contains("mumbai") || dest.contains("bangalore") ||
                dest.contains("london") || dest.contains("paris") || dest.contains("new york") ||
                dest.contains("tokyo") || dest.contains("singapore") || dest.contains("dubai") ||
                dest.contains("bangkok") || dest.contains("city") || dest.contains("metro") ||
                dest.contains("kolkata") || dest.contains("chennai") || dest.contains("hyderabad") ||
                dest.contains("pune") || dest.contains("ahmedabad") || dest.contains("berlin") ||
                dest.contains("amsterdam") || dest.contains("barcelona") || dest.contains("rome") ||
                dest.contains("sydney") || dest.contains("toronto") || dest.contains("hong kong")) {
            isCityDest = true;
        }

        // Religious / heritage keywords
        if (dest.contains("varanasi") || dest.contains("haridwar") || dest.contains("rishikesh") ||
                dest.contains("tirupati") || dest.contains("amritsar") || dest.contains("vatican") ||
                dest.contains("mecca") || dest.contains("jerusalem") || dest.contains("temple") ||
                dest.contains("pilgrimage") || dest.contains("puri") || dest.contains("mathura") ||
                dest.contains("vrindavan") || dest.contains("shirdi") || dest.contains("ayodhya")) {
            isReligiousDest = true;
        }
    }

    void parseGeoAndAnalyze(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray results = root.optJSONArray("results");
            if (results != null && results.length() > 0) {
                JSONObject loc = results.getJSONObject(0);
                double lat = loc.getDouble("latitude");
                double lon = loc.getDouble("longitude");
                String country = loc.optString("country", "").toLowerCase();
                String featureCode = loc.optString("feature_code", "").toLowerCase();

                // Use country to infer more context
                if (country.contains("maldives") || country.contains("thailand") ||
                        country.contains("indonesia") || country.contains("philippines") ||
                        country.contains("sri lanka")) {
                    isBeachDest = true;
                }
                if (country.contains("iceland") || country.contains("norway") ||
                        country.contains("finland") || country.contains("sweden")) {
                    isColdDest = true;
                }

                // feature_code PPL = city, MT = mountain, etc.
                if (featureCode.startsWith("ppl") || featureCode.startsWith("adm")) {
                    isCityDest = true;
                }

                fetchWeatherForPacking(lat, lon, true);
            } else {
                fetchWeatherForPacking(0, 0, false);
            }
        } catch (Exception e) {
            fetchWeatherForPacking(0, 0, false);
        }
    }

    // ── Fetch weather to know rain/hot/cold for packing ──
    void fetchWeatherForPacking(double lat, double lon, boolean hasCoords) {
        if (!hasCoords) {
            buildPackList(false, false, false, false);
            return;
        }

        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        java.util.Calendar startCal = java.util.Calendar.getInstance();
        startCal.setTimeInMillis(startDate);
        java.util.Calendar endCal = java.util.Calendar.getInstance();
        endCal.setTimeInMillis(endDate);
        java.util.Calendar maxEnd = java.util.Calendar.getInstance();
        maxEnd.add(java.util.Calendar.DAY_OF_MONTH, 15);
        if (endCal.after(maxEnd)) endCal = maxEnd;
        java.util.Calendar today = java.util.Calendar.getInstance();
        if (startCal.before(today)) startCal = today;

        String url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=" + lat + "&longitude=" + lon +
                "&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_sum,windspeed_10m_max" +
                "&timezone=auto" +
                "&start_date=" + fmt.format(startCal.getTime()) +
                "&end_date=" + fmt.format(endCal.getTime());

        Request request = new Request.Builder().url(url).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> buildPackList(false, false, false, false));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> buildPackList(false, false, false, false));
                    return;
                }
                String body = response.body().string();
                mainHandler.post(() -> parseWeatherAndBuildList(body));
            }
        });
    }

    void parseWeatherAndBuildList(String json) {
        boolean willRain = false, willBeHot = false, willBeCold = false, highWind = false;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject daily = root.getJSONObject("daily");
            JSONArray codes = daily.getJSONArray("weathercode");
            JSONArray maxTemps = daily.getJSONArray("temperature_2m_max");
            JSONArray minTemps = daily.getJSONArray("temperature_2m_min");
            JSONArray precip = daily.getJSONArray("precipitation_sum");
            JSONArray wind = daily.getJSONArray("windspeed_10m_max");

            for (int i = 0; i < codes.length(); i++) {
                int code = codes.getInt(i);
                float tMax = (float) maxTemps.getDouble(i);
                float tMin = (float) minTemps.getDouble(i);
                float rain = (float) precip.getDouble(i);
                float w = (float) wind.getDouble(i);

                if (tMax > 32) willBeHot = true;
                if (tMin < 15) willBeCold = true;
                if (w > 40) highWind = true;
                if (rain > 1.0 || (code >= 51 && code <= 67) || (code >= 80 && code <= 99)) willRain = true;
            }
        } catch (Exception e) {
            // ignore, use defaults
        }
        buildPackList(willRain, willBeHot, willBeCold, highWind);
    }

    void buildPackList(boolean willRain, boolean willBeHot, boolean willBeCold, boolean highWind) {
        ArrayList<PackItem> items = new ArrayList<>();

        // ── Always pack ──────────────────────────────────────────
        items.add(new PackItem("📄 Passport / ID", "Always required"));
        items.add(new PackItem("💳 Wallet & cards", "Keep cash + cards"));
        items.add(new PackItem("📱 Phone + charger", "Don't forget the cable!"));
        items.add(new PackItem("💊 Personal medicines", "Check expiry dates"));
        items.add(new PackItem("🪥 Toothbrush & toothpaste", "Basic toiletries"));
        items.add(new PackItem("👕 Clothes per day", "Plus 1 extra set"));
        items.add(new PackItem("🩲 Underwear & socks", "Pack extra"));
        items.add(new PackItem("👟 Comfortable shoes", "For walking"));
        items.add(new PackItem("🎒 Backpack / day bag", "For daily outings"));
        items.add(new PackItem("🔋 Power bank", "Never run out of battery"));

        // ── Destination-specific packing ─────────────────────────
        if (isBeachDest) {
            items.add(new PackItem("🩱 Swimwear", destination + " is a beach destination!"));
            items.add(new PackItem("🏖️ Beach towel", "Essential for beach days"));
            items.add(new PackItem("👡 Flip flops / sandals", "Sand-friendly footwear"));
            items.add(new PackItem("🧴 Sunscreen SPF 50+", "Strong sun at the beach"));
            items.add(new PackItem("🕶️ Sunglasses", "UV protection"));
            items.add(new PackItem("🧢 Hat / cap", "Shield from the beach sun"));
            items.add(new PackItem("📸 Waterproof phone case", "Protect your phone near water"));
            items.add(new PackItem("🧴 After-sun lotion", "Soothe sun-exposed skin"));
            items.add(new PackItem("💧 Water bottle", "Stay hydrated in the heat"));
        }

        if (isMountainDest) {
            items.add(new PackItem("🥾 Trekking / hiking boots", destination + " has hilly terrain"));
            items.add(new PackItem("🧥 Warm jacket / fleece", "Temperatures drop at altitude"));
            items.add(new PackItem("🧣 Scarf / neck warmer", "Cold mountain winds"));
            items.add(new PackItem("🧤 Gloves", "For cold mornings/evenings"));
            items.add(new PackItem("🧢 Woolen hat / beanie", "Essential in the hills"));
            items.add(new PackItem("🗺️ Offline map / GPS app", "Connectivity may be poor"));
            items.add(new PackItem("🩹 Basic first aid kit", "Blisters and scrapes"));
            items.add(new PackItem("💧 Hydration bottle", "Stay hydrated while trekking"));
            items.add(new PackItem("🥜 Energy snacks / bars", "For day hikes"));
            items.add(new PackItem("☀️ Sunscreen SPF 50+", "UV is stronger at altitude"));
        }

        if (isDesertDest) {
            items.add(new PackItem("🧕 Light full-sleeve clothes", destination + " can be very hot & sunny"));
            items.add(new PackItem("🧢 Wide-brim hat", "Essential sun protection in the desert"));
            items.add(new PackItem("🕶️ UV-protection sunglasses", "Intense desert glare"));
            items.add(new PackItem("🧴 Sunscreen SPF 50+", "Desert sun is harsh"));
            items.add(new PackItem("💧 Large water bottle", "Dehydration risk is high"));
            items.add(new PackItem("🧣 Light scarf / shawl", "Sand protection + evening warmth"));
            items.add(new PackItem("👟 Closed-toe comfortable shoes", "Sand can get into open sandals"));
        }

        if (isColdDest) {
            items.add(new PackItem("🧥 Heavy winter jacket", destination + " can be very cold"));
            items.add(new PackItem("🧣 Thick scarf", "Essential for cold weather"));
            items.add(new PackItem("🧤 Insulated gloves", "Frostbite prevention"));
            items.add(new PackItem("🧢 Thermal beanie", "Keep your head warm"));
            items.add(new PackItem("🧦 Thermal socks / layers", "Layering is key in the cold"));
            items.add(new PackItem("👢 Waterproof winter boots", "Snow / slush protection"));
            items.add(new PackItem("🔥 Hand warmers", "Useful for outdoor sightseeing"));
        }

        if (isCityDest && !isBeachDest && !isMountainDest) {
            items.add(new PackItem("👔 Smart-casual outfit", "For restaurants and city outings"));
            items.add(new PackItem("🗺️ City transit card / app", "Metro / bus for " + destination));
            items.add(new PackItem("📷 Camera", "Capture city sights"));
            items.add(new PackItem("🔒 Anti-theft bag / money belt", "Urban pickpocket safety"));
        }

        if (isReligiousDest) {
            items.add(new PackItem("👘 Modest / covering clothing", destination + " has religious sites that require it"));
            items.add(new PackItem("🧦 Extra socks", "Many temples require removing shoes"));
            items.add(new PackItem("🧣 Dupatta / stole / shawl", "Head covering for religious places"));
        }

        // ── Weather-based ─────────────────────────────────────────
        if (willRain) {
            items.add(new PackItem("☂️ Umbrella", "Rain expected during your trip!"));
            items.add(new PackItem("🧥 Waterproof jacket", "Stay dry in the rain"));
            items.add(new PackItem("👞 Waterproof / closed shoes", "Avoid wet feet"));
        }

        if (willBeHot && !isBeachDest && !isDesertDest) {
            items.add(new PackItem("🧴 Sunscreen SPF 50+", "Hot weather ahead!"));
            items.add(new PackItem("🕶️ Sunglasses", "UV protection"));
            items.add(new PackItem("🧢 Hat / cap", "Shield from the sun"));
            items.add(new PackItem("💧 Water bottle", "Stay hydrated in the heat"));
        }

        if (willBeCold && !isMountainDest && !isColdDest) {
            items.add(new PackItem("🧥 Warm jacket / hoodie", "Temperatures may drop"));
            items.add(new PackItem("🧣 Scarf", "For cold nights"));
        }

        if (highWind) {
            items.add(new PackItem("🎩 Windproof hat / cap", "Strong winds expected"));
        }

        // ── Activity-based ────────────────────────────────────────
        for (String activity : activities) {
            switch (activity.toLowerCase()) {
                case "beach":
                    if (!isBeachDest) { // avoid duplicates if already beach dest
                        items.add(new PackItem("🩱 Swimwear", "For beach activities"));
                        items.add(new PackItem("🏖️ Beach towel", "Don't rely on hotels"));
                    }
                    break;
                case "hiking":
                    if (!isMountainDest) {
                        items.add(new PackItem("🥾 Hiking boots", "Good ankle support"));
                        items.add(new PackItem("🩹 First aid kit", "Blisters and scrapes happen"));
                        items.add(new PackItem("🥜 Trail snacks / energy bars", "Keep energy up"));
                    }
                    items.add(new PackItem("🗺️ Offline map / GPS", "In case of no signal"));
                    break;
                case "shopping":
                    items.add(new PackItem("🛍️ Foldable tote bag", "Extra carry space for purchases"));
                    items.add(new PackItem("💵 Extra cash", "Some shops don't take cards"));
                    items.add(new PackItem("📋 Shopping list", "Plan what to buy"));
                    break;
                case "sightseeing":
                    items.add(new PackItem("📷 Camera", "Capture the memories"));
                    items.add(new PackItem("📖 Travel guidebook / app", "Learn about each spot"));
                    items.add(new PackItem("🎟️ Pre-book tickets", "Skip queues at popular sites"));
                    break;
                case "food":
                    items.add(new PackItem("📝 Restaurant bucket list", "Research local eateries beforehand"));
                    items.add(new PackItem("🤢 Antacids / digestion tablets", "Try local food safely"));
                    items.add(new PackItem("🧻 Tissues / wet wipes", "Street food essentials"));
                    break;
                case "camping":
                    items.add(new PackItem("🏕️ Tent", "Check poles and pegs"));
                    items.add(new PackItem("🛌 Sleeping bag", "Suitable for the weather"));
                    items.add(new PackItem("🔦 Torch / headlamp", "Night navigation"));
                    items.add(new PackItem("🪲 Insect repellent", "Keep the bugs away"));
                    items.add(new PackItem("🔥 Lighter / matches", "Campfire essentials"));
                    items.add(new PackItem("🫙 Camp cooking gear", "Pots, cups, utensils"));
                    break;
                case "adventure":
                    items.add(new PackItem("⛑️ Helmet (if needed)", "Safety first"));
                    items.add(new PackItem("📄 Insurance documents", "Cover adventure sports"));
                    items.add(new PackItem("💪 Energy supplements", "For high-intensity activities"));
                    break;
            }
        }

        showPackList(items);
    }

    void showPackList(ArrayList<PackItem> items) {
        updateProgress(items);

        ArrayAdapter<PackItem> adapter = new ArrayAdapter<PackItem>(requireContext(),
                R.layout.item_pack, items) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_pack, parent, false);
                }
                PackItem item = getItem(position);
                CheckBox checkBox = convertView.findViewById(R.id.checkItem);
                TextView tvName = convertView.findViewById(R.id.tvItemName);
                TextView tvReason = convertView.findViewById(R.id.tvItemReason);

                checkBox.setOnCheckedChangeListener(null);
                tvName.setText(item.name);
                tvReason.setText(item.reason);
                checkBox.setChecked(item.checked);

                if (item.checked) {
                    tvName.setPaintFlags(tvName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    tvName.setAlpha(0.5f);
                } else {
                    tvName.setPaintFlags(tvName.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                    tvName.setAlpha(1.0f);
                }

                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    item.checked = isChecked;
                    if (isChecked) {
                        tvName.setPaintFlags(tvName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                        tvName.setAlpha(0.5f);
                    } else {
                        tvName.setPaintFlags(tvName.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                        tvName.setAlpha(1.0f);
                    }
                    updateProgress(items);
                });

                return convertView;
            }
        };
        listPackItems.setAdapter(adapter);
    }

    void updateProgress(ArrayList<PackItem> items) {
        int total = items.size();
        int done = 0;
        for (PackItem item : items) if (item.checked) done++;
        tvPackProgress.setText(done + " / " + total + " packed");
        packProgressBar.setProgress(total == 0 ? 0 : (done * 100) / total);
    }
}
