# TripPlanner

A modern Android application for planning trips, discovering top-rated local attractions, and checking weather forecasts.

## Features
- **Destination Discovery**: Search for cities globally with auto-complete functionality.
- **Top Attractions & Dining**: Automatically fetches the best sights, restaurants, and hotels using the **Google Places API (New)**.
- **Real-time Photos**: High-quality venue photos fetched directly from Google Maps.
- **Weather Forecast**: Real-time localized weather data powered by Open-Meteo.
- **Secure Architecture**: API keys are isolated from the codebase using `local.properties` and BuildConfig.

## Architecture & APIs
- **Google Places API (New)**: Semantic Text Search and Places Photo API for rich point-of-interest data.
- **Open-Meteo API**: Keyless, high-performance geocoding and weather forecasting.
- **Glide**: Efficient image loading and caching.
- **OkHttp**: Robust HTTP client for parallel asynchronous API requests.

## Setup Instructions

This repository does **not** include the required API keys for security reasons. You must provide your own Google Cloud API key.

1. Clone the repository.
2. In the root directory (same level as `app/`), create a file named `local.properties`.
3. Add your Google API key to the file like this:
   ```properties
   places.api.key=YOUR_ACTUAL_API_KEY_HERE
   ```
4. Open the project in Android Studio.
5. Sync Project with Gradle Files (the elephant icon).
6. Build and run the app.

> **Note**: Your Google Cloud project must have the **Places API (New)** enabled to retrieve attraction data and photos.

## Building
To build a debug APK via command line:
```bash
./gradlew assembleDebug
```
