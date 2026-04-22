package com.example.tripplanner;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WeatherFragment extends Fragment {

    String destination;
    long startDate, endDate;

    TextView tvLoading;
    LinearLayout weatherContainer;
    CardView cardWeatherTip;
    TextView tvWeatherTip;

    OkHttpClient httpClient = new OkHttpClient();
    Handler mainHandler = new Handler(Looper.getMainLooper());

    static class DayWeather {
        String date;
        float tempMin;
        float tempMax;
        int humidity;
        float windSpeed;
        String condition;
        String description;
        boolean willRain;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_weather, container, false);

        tvLoading = view.findViewById(R.id.tvWeatherLoading);
        weatherContainer = view.findViewById(R.id.weatherContainer);
        cardWeatherTip = view.findViewById(R.id.cardWeatherTip);
        tvWeatherTip = view.findViewById(R.id.tvWeatherTip);

        if (getArguments() != null) {
            destination = getArguments().getString("destination");
            startDate = getArguments().getLong("startDate");
            endDate = getArguments().getLong("endDate");
        }

        fetchLocationThenWeather();
        return view;
    }

    void fetchLocationThenWeather() {
        // Use Open-Meteo Geocoding (free, no API key needed) to get lat/lon
        try {
            String encoded = URLEncoder.encode(destination, "UTF-8");
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + encoded + "&count=1&language=en&format=json";

            Request request = new Request.Builder().url(geoUrl).build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    mainHandler.post(() -> tvLoading.setText("⚠️ Connection error."));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> tvLoading.setText("⚠️ City not found."));
                        return;
                    }

                    try {
                        String body = response.body().string();
                        JSONObject root = new JSONObject(body);
                        JSONArray results = root.optJSONArray("results");

                        if (results != null && results.length() > 0) {
                            JSONObject city = results.getJSONObject(0);
                            double lat = city.getDouble("latitude");
                            double lon = city.getDouble("longitude");
                            mainHandler.post(() -> fetchOpenMeteoWeather(lat, lon));
                        } else {
                            mainHandler.post(() -> tvLoading.setText("⚠️ City not found."));
                        }
                    } catch (Exception e) {
                        mainHandler.post(() -> tvLoading.setText("⚠️ Error finding location."));
                    }
                }
            });
        } catch (Exception e) {
            tvLoading.setText("⚠️ Error encoding destination.");
        }
    }

    void fetchOpenMeteoWeather(double lat, double lon) {
        // Fetch 14 days of weather from Open-Meteo (Free, no key needed)
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat +
                "&longitude=" + lon +
                "&daily=weathercode,temperature_2m_max,temperature_2m_min,precipitation_probability_max,windspeed_10m_max&timezone=auto&forecast_days=14";

        Request request = new Request.Builder().url(url).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> tvLoading.setText("⚠️ Weather service unavailable."));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> tvLoading.setText("⚠️ Weather data error."));
                    return;
                }

                String body = response.body().string();
                mainHandler.post(() -> parseAndShowOpenMeteo(body));
            }
        });
    }

    void parseAndShowOpenMeteo(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject daily = root.getJSONObject("daily");
            JSONArray timeArr = daily.getJSONArray("time");
            JSONArray maxTempArr = daily.getJSONArray("temperature_2m_max");
            JSONArray minTempArr = daily.getJSONArray("temperature_2m_min");
            JSONArray codeArr = daily.getJSONArray("weathercode");
            JSONArray humArr = daily.getJSONArray("precipitation_probability_max");
            JSONArray windArr = daily.getJSONArray("windspeed_10m_max");

            ArrayList<DayWeather> tripWeather = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

            // User's trip dates
            long start = truncateDate(startDate);
            long end = truncateDate(endDate);

            for (int i = 0; i < timeArr.length(); i++) {
                String dateStr = timeArr.getString(i);
                long dayTime = sdf.parse(dateStr).getTime();

                // Only show weather if it's within the trip range
                if (dayTime >= start && dayTime <= end) {
                    DayWeather dw = new DayWeather();
                    dw.date = dateStr;
                    dw.tempMax = (float) maxTempArr.getDouble(i);
                    dw.tempMin = (float) minTempArr.getDouble(i);
                    dw.humidity = humArr.getInt(i);
                    dw.windSpeed = (float) windArr.getDouble(i);

                    int code = codeArr.getInt(i);
                    dw.condition = getConditionFromCode(code);
                    dw.description = getDescriptionFromCode(code);
                    dw.willRain = code >= 51; // WMO codes 51+ are rain/snow

                    tripWeather.add(dw);
                }
            }

            if (tripWeather.isEmpty()) {
                // Fallback: Show the first few days if trip is too far in future
                for (int i = 0; i < Math.min(7, timeArr.length()); i++) {
                    DayWeather dw = new DayWeather();
                    dw.date = timeArr.getString(i);
                    dw.tempMax = (float) maxTempArr.getDouble(i);
                    dw.tempMin = (float) minTempArr.getDouble(i);
                    dw.humidity = humArr.getInt(i);
                    dw.windSpeed = (float) windArr.getDouble(i);
                    dw.condition = getConditionFromCode(codeArr.getInt(i));
                    dw.description = getDescriptionFromCode(codeArr.getInt(i));
                    dw.willRain = codeArr.getInt(i) >= 51;
                    tripWeather.add(dw);
                }
            }

            showWeatherCards(tripWeather);

        } catch (Exception e) {
            tvLoading.setText("⚠️ Error parsing weather.");
        }
    }

    long truncateDate(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    String getConditionFromCode(int code) {
        if (code <= 3) return "Clear";
        if (code <= 48) return "Clouds";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Rain";
        return "Thunderstorm";
    }

    String getDescriptionFromCode(int code) {
        switch (code) {
            case 0: return "Clear sky";
            case 1: case 2: case 3: return "Partly cloudy";
            case 45: case 48: return "Foggy";
            case 51: case 53: case 55: return "Drizzle";
            case 61: case 63: case 65: return "Rainy";
            case 71: case 73: case 75: return "Snowy";
            case 95: case 96: case 99: return "Thunderstorm";
            default: return "Cloudy";
        }
    }

    void showWeatherCards(ArrayList<DayWeather> days) {
        tvLoading.setVisibility(View.GONE);
        weatherContainer.setVisibility(View.VISIBLE);
        weatherContainer.removeAllViews();

        SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat outFmt = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());

        boolean anyRain = false;
        boolean anyHot = false;
        boolean anyCold = false;

        for (DayWeather dw : days) {
            View card = LayoutInflater.from(getContext()).inflate(R.layout.item_weather_day, weatherContainer, false);

            TextView tvDate = card.findViewById(R.id.tvDayDate);
            TextView tvEmoji = card.findViewById(R.id.tvDayEmoji);
            TextView tvCondition = card.findViewById(R.id.tvDayCondition);
            TextView tvTemp = card.findViewById(R.id.tvDayTemp);
            TextView tvHumidity = card.findViewById(R.id.tvDayHumidity);
            TextView tvWind = card.findViewById(R.id.tvDayWind);
            TextView tvRainWarning = card.findViewById(R.id.tvRainWarning);

            try {
                tvDate.setText(outFmt.format(inFmt.parse(dw.date)));
            } catch (Exception e) {
                tvDate.setText(dw.date);
            }

            tvEmoji.setText(getWeatherEmoji(dw.condition));
            tvCondition.setText(dw.description);
            tvTemp.setText(Math.round(dw.tempMin) + "–" + Math.round(dw.tempMax) + "°C");
            tvHumidity.setText("🌧 " + dw.humidity + "%");
            tvWind.setText(Math.round(dw.windSpeed) + " km/h");

            if (dw.willRain) {
                tvRainWarning.setVisibility(View.VISIBLE);
                anyRain = true;
            }

            if (dw.tempMax > 32) anyHot = true;
            if (dw.tempMin < 15) anyCold = true;

            weatherContainer.addView(card);
        }

        buildWeatherTip(anyRain, anyHot, anyCold, days);
    }

    void buildWeatherTip(boolean anyRain, boolean anyHot, boolean anyCold, ArrayList<DayWeather> days) {
        cardWeatherTip.setVisibility(View.VISIBLE);
        StringBuilder tip = new StringBuilder();

        if (anyRain) tip.append("🌧️ Rain is expected on some days — pack an umbrella.\n\n");
        if (anyHot) tip.append("☀️ It's going to be hot — pack sunscreen and light clothes.\n\n");
        if (anyCold) tip.append("🧥 Nights might be cool — bring a light jacket.\n\n");

        if (tip.length() == 0) {
            tip.append("✅ Weather looks pleasant! Pack comfortable clothes.");
        }

        tvWeatherTip.setText(tip.toString().trim());
    }

    String getWeatherEmoji(String condition) {
        switch (condition) {
            case "Clear": return "☀️";
            case "Clouds": return "☁️";
            case "Rain": return "🌧️";
            case "Snow": return "❄️";
            case "Thunderstorm": return "⛈️";
            default: return "🌤️";
        }
    }
}