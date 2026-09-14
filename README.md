# SkyCast

A free, ad-free, no-tracking weather app for Android. Native Kotlin + Jetpack Compose.
Weather data comes from [Open-Meteo](https://open-meteo.com) — **no API key, no account**.

## Features

- Current conditions with a large temperature readout on a temperature dial
  (today's low → high, with a cool→warm gradient)
- "Feels like", humidity, wind speed + direction, today's high/low
- Next-24-hours hourly strip and a 7-day forecast
- °C ⇄ °F toggle (remembered between launches; wind unit follows)
- Use current GPS location **or** search any city by name
- Last-viewed place is remembered, so it opens straight to your weather
- Animated precipitation **radar** on an interactive map (RainViewer radar over a
  MapTiler dark base map) — play/pause and a scrubbable timeline
- **No ads, no analytics, no account.** Network calls go only to the weather/map
  data sources: Open-Meteo, RainViewer (radar), and MapTiler (base map; map data
  © OpenStreetMap).

## Requirements

- Android Studio (bundled JDK 21 is used for command-line builds)
- Android SDK Platform 34, Build-Tools 34.0.0+
- A device/emulator running **Android 8.0 (API 26)** or newer

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── java/com/spritzlive/weather/
│   ├── MainActivity.kt         # permission flow + wiring
│   ├── WeatherViewModel.kt     # UI state, loading/error handling
│   ├── WeatherRepository.kt    # Open-Meteo geocoding + forecast + RainViewer radar (HttpURLConnection + org.json)
│   ├── LocationProvider.kt     # FusedLocationProvider one-shot current fix
│   ├── WeatherModels.kt        # data classes, WMO code → condition mapping, radar frames
│   ├── WeatherPrefs.kt         # persisted unit + last place
│   └── ui/
│       ├── WeatherScreen.kt    # main screen (search, current, hourly, radar, daily)
│       ├── RadarCard.kt        # osmdroid map + animated RainViewer radar overlay
│       ├── TempDial.kt         # Canvas-drawn temperature dial
│       └── Theme.kt
└── res/                        # theme, colors, adaptive launcher icon
```

## Data source

Open-Meteo, key-less:

- Geocoding (city search): `https://geocoding-api.open-meteo.com/v1/search`
- Forecast: `https://api.open-meteo.com/v1/forecast`

Parsing is manual via the built-in `org.json`, so the app needs no serialization
or HTTP library — just `HttpURLConnection` on a background dispatcher.

Radar is layered on top, also key-less:

- Radar frames (past ~2h + short nowcast): `https://api.rainviewer.com/public/weather-maps.json`
- Radar tiles: `{host}{path}/256/{z}/{x}/{y}/{color}/{options}.png` (RainViewer)
- Base map tiles: **MapTiler** dark style (`dataviz-dark`), rendered by **osmdroid**.
  MapTiler's free tier permits production use; map data is © OpenStreetMap.

The MapTiler key lives in a git-ignored `maps.properties` at the repo root
(`maptilerKey=...`), surfaced to code as `BuildConfig.MAPTILER_KEY`. **Without a
key the base map falls back to OpenStreetMap's Mapnik tiles** so dev builds still
work — but OSM's public tiles aren't sanctioned for a published app, so ship with
a key. In the MapTiler dashboard, restrict the key with **Allowed user-agent
header = `com.spritzlive.weather`** — the app already sends that as its User-Agent
(osmdroid `userAgentValue = packageName`). This is light protection (a User-Agent
is spoofable) but, with the free-tier usage cap and the key kept out of git, it's
reasonable for a client-only app. MapTiler's stronger digital-signature option
needs a server-side secret we don't have, so it doesn't help a keys-in-APK app.

## Building from the command line

The machine's default `java` may be a JRE that can't build. Point `JAVA_HOME` at
Android Studio's bundled JDK 21 first:

```bash
# Git Bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew.bat :app:assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Install on a connected device/emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or just open the folder in Android Studio and press ▶.

## Release signing

Signing is wired the same way as the Speedometer project: drop a
`keystore.properties` at the repo root with `storeFile`, `storePassword`,
`keyAlias`, `keyPassword`, and the release build will sign automatically. Without
it, the release build still assembles (unsigned). Both the keystore and
`keystore.properties` are git-ignored.

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew.bat :app:bundleRelease   # -> app-release.aab (upload to Play)
./gradlew.bat :app:assembleRelease # -> app-release.apk (sideload testing)
```

## Notes

- `applicationId` is `com.spritzlive.weather`. It is **permanent once published** —
  change it now if you want a different package name.
- Open-Meteo is free for non-commercial use with generous limits and needs no key.
  For heavy commercial use see their paid tier.
