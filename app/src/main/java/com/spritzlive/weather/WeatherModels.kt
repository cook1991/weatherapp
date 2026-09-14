package com.spritzlive.weather

/** Temperature unit for display. Open-Meteo does the conversion server-side. */
enum class TempUnit(
    val apiValue: String,
    /** °C / °F suffix shown after a number. */
    val degree: String,
    /** Wind-speed unit paired with this temperature unit. */
    val windApiValue: String,
    val windLabel: String,
) {
    CELSIUS("celsius", "°C", "kmh", "km/h"),
    FAHRENHEIT("fahrenheit", "°F", "mph", "mph");

    fun other(): TempUnit = if (this == CELSIUS) FAHRENHEIT else CELSIUS
}

/**
 * A place to show weather for. Comes either from the geocoding search or from a
 * reverse-geocode of the device's current GPS fix.
 */
data class Place(
    val name: String,
    val admin: String?,   // state/region, e.g. "California"
    val country: String?,
    val latitude: Double,
    val longitude: Double,
) {
    /** "Portland, Oregon" style label; falls back gracefully when parts are missing. */
    val label: String
        get() = listOfNotNull(name, admin ?: country).joinToString(", ")
}

/** One hour of the short-term forecast. */
data class HourForecast(
    /** Epoch-agnostic hour label, e.g. "3 PM" (already localized to the place's timezone). */
    val hourLabel: String,
    val temperature: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    /** Chance of precipitation for this hour, 0–100. */
    val precipitationProbability: Int,
)

/** One day of the multi-day forecast. */
data class DayForecast(
    /** e.g. "Mon"; "Today" for the first entry. */
    val dayLabel: String,
    val high: Double,
    val low: Double,
    val condition: WeatherCondition,
    /** Peak chance of precipitation across the day, 0–100. */
    val precipitationProbability: Int,
)

/** Everything the current-conditions card needs. */
data class CurrentWeather(
    val temperature: Double,
    val apparentTemperature: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val humidityPercent: Int,
    /** Chance of precipitation for the current hour, 0–100. */
    val precipitationProbability: Int,
    val windSpeed: Double,
    val windDirectionDeg: Int,
    /** The daily high/low for today, used to scale the temperature dial. */
    val todayHigh: Double,
    val todayLow: Double,
    /** Today's sunrise / sunset, pre-formatted for display (e.g. "6:12 AM"). */
    val sunrise: String,
    val sunset: String,
)

/**
 * One RainViewer radar frame. [timestampSec] is a Unix epoch (seconds, UTC);
 * [path] is the API path segment for the frame's tiles. [isForecast] marks the
 * short-range nowcast frames (the future), as opposed to observed past frames.
 */
data class RadarFrame(
    val timestampSec: Long,
    val path: String,
    val isForecast: Boolean,
)

/**
 * The set of animatable radar frames plus the tile [host] they load from. Frames
 * are ordered oldest → newest (past first, then nowcast). Radar coverage is global,
 * so this is independent of the chosen place — only the map is centered per-place.
 */
data class RadarData(
    val host: String,
    val frames: List<RadarFrame>,
) {
    /** Index of the last observed (non-forecast) frame — the natural "now". */
    val nowIndex: Int
        get() = frames.indexOfLast { !it.isForecast }.coerceAtLeast(0)

    /** RainViewer 256px tile-URL template for [frame]: {host}{path}/256/{z}/{x}/{y}/{color}/{opts}.png */
    fun tileUrlBase(frame: RadarFrame): String = host + frame.path
}

/** A fully-resolved forecast for one [Place] in one [TempUnit]. */
data class Forecast(
    val place: Place,
    val unit: TempUnit,
    val current: CurrentWeather,
    val hourly: List<HourForecast>,
    val daily: List<DayForecast>,
)

/**
 * WMO weather-interpretation codes, grouped into the handful of buckets we draw
 * differently. Open-Meteo reports the raw integer code; [fromCode] maps it.
 */
enum class WeatherCondition(val description: String, val emoji: String) {
    CLEAR("Clear sky", "☀️"),
    MAINLY_CLEAR("Mainly clear", "🌤️"),
    PARTLY_CLOUDY("Partly cloudy", "⛅"),
    OVERCAST("Overcast", "☁️"),
    FOG("Fog", "🌫️"),
    DRIZZLE("Drizzle", "🌦️"),
    FREEZING_DRIZZLE("Freezing drizzle", "🌧️"),
    RAIN("Rain", "🌧️"),
    FREEZING_RAIN("Freezing rain", "🌧️"),
    SNOW("Snow", "🌨️"),
    SHOWERS("Rain showers", "🌦️"),
    SNOW_SHOWERS("Snow showers", "🌨️"),
    THUNDERSTORM("Thunderstorm", "⛈️"),
    UNKNOWN("—", "❓");

    companion object {
        fun fromCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1 -> MAINLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            56, 57 -> FREEZING_DRIZZLE
            61, 63, 65 -> RAIN
            66, 67 -> FREEZING_RAIN
            71, 73, 75, 77 -> SNOW
            80, 81, 82 -> SHOWERS
            85, 86 -> SNOW_SHOWERS
            95, 96, 99 -> THUNDERSTORM
            else -> UNKNOWN
        }
    }
}

/** Compass point (N, NE, …) for a wind bearing in degrees. */
fun windCompass(deg: Int): String {
    val points = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return points[(((deg % 360) + 360) % 360 + 22) / 45 % 8]
}
