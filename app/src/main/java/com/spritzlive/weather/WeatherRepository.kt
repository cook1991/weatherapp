package com.spritzlive.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Talks to the free, key-less Open-Meteo API. Two endpoints are used:
 *  - geocoding-api.open-meteo.com  → city search (name → lat/lon)
 *  - api.open-meteo.com/v1/forecast → current + hourly + daily weather
 *
 * All parsing is manual via org.json so the app needs no serialization library.
 * Network calls run on [Dispatchers.IO]; every public method either returns data
 * or throws, and the ViewModel turns exceptions into a friendly error state.
 */
class WeatherRepository {

    /** Look up places matching a free-text query. Empty list if nothing matches. */
    suspend fun search(query: String): List<Place> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=en&format=json"
        val json = JSONObject(httpGet(url))
        val results = json.optJSONArray("results") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until results.length()) {
                val o = results.getJSONObject(i)
                add(
                    Place(
                        name = o.getString("name"),
                        admin = o.optString("admin1", "").ifBlank { null },
                        country = o.optString("country", "").ifBlank { null },
                        latitude = o.getDouble("latitude"),
                        longitude = o.getDouble("longitude"),
                    )
                )
            }
        }
    }

    /**
     * Reverse-geocode a coordinate via BigDataCloud's free, key-less client
     * endpoint. Used as a fallback when the on-device geocoder has no backend
     * (e.g. a device without Google Play Services). Returns null if the lookup
     * fails or yields no usable place name.
     */
    suspend fun reverseGeocode(lat: Double, lon: Double): Place? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.bigdatacloud.net/data/reverse-geocode-client" +
                "?latitude=$lat&longitude=$lon&localityLanguage=en"
            val o = JSONObject(httpGet(url))
            val name = o.optString("city", "").ifBlank { o.optString("locality", "") }.ifBlank { null }
                ?: return@runCatching null
            Place(
                name = name,
                admin = o.optString("principalSubdivision", "").ifBlank { null },
                country = o.optString("countryName", "").ifBlank { null },
                latitude = lat,
                longitude = lon,
            )
        }.getOrNull()
    }

    /** Fetch the full forecast for [place] in [unit]. */
    suspend fun forecast(place: Place, unit: TempUnit): Forecast = withContext(Dispatchers.IO) {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=").append(place.latitude)
            append("&longitude=").append(place.longitude)
            append("&current=temperature_2m,relative_humidity_2m,apparent_temperature,")
            append("is_day,weather_code,wind_speed_10m,wind_direction_10m")
            append("&hourly=temperature_2m,weather_code,is_day,precipitation_probability")
            append("&daily=weather_code,temperature_2m_max,temperature_2m_min,")
            append("sunrise,sunset,precipitation_probability_max")
            append("&temperature_unit=").append(unit.apiValue)
            append("&wind_speed_unit=").append(unit.windApiValue)
            append("&timezone=auto&forecast_days=7")
        }
        parseForecast(JSONObject(httpGet(url)), place, unit)
    }

    /**
     * Fetch the animatable radar frame list from RainViewer's free, key-less
     * weather-maps endpoint. Best-effort: returns null (rather than throwing) on any
     * failure, so a radar outage never blocks the weather forecast from showing.
     */
    suspend fun radar(): RadarData? = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(httpGet("https://api.rainviewer.com/public/weather-maps.json"))
            val host = root.getString("host")
            val radar = root.getJSONObject("radar")
            val frames = buildList {
                addFramesFrom(radar.optJSONArray("past"), isForecast = false)
                addFramesFrom(radar.optJSONArray("nowcast"), isForecast = true)
            }
            if (frames.isEmpty()) null else RadarData(host, frames)
        }.getOrNull()
    }

    private fun MutableList<RadarFrame>.addFramesFrom(arr: org.json.JSONArray?, isForecast: Boolean) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(RadarFrame(o.getLong("time"), o.getString("path"), isForecast))
        }
    }

    private fun parseForecast(root: JSONObject, place: Place, unit: TempUnit): Forecast {
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val dailyTimes = daily.getJSONArray("time")
        val dailyMax = daily.getJSONArray("temperature_2m_max")
        val dailyMin = daily.getJSONArray("temperature_2m_min")
        val dailyCodes = daily.getJSONArray("weather_code")
        val dailySunrise = daily.getJSONArray("sunrise")
        val dailySunset = daily.getJSONArray("sunset")
        val dailyPrecip = daily.getJSONArray("precipitation_probability_max")

        // Hourly arrays + the index of the current hour (parsed first so the current
        // conditions can borrow the current hour's precipitation probability).
        val hourly = root.getJSONObject("hourly")
        val hourTimes = hourly.getJSONArray("time")
        val hourTemps = hourly.getJSONArray("temperature_2m")
        val hourCodes = hourly.getJSONArray("weather_code")
        val hourIsDay = hourly.getJSONArray("is_day")
        val hourPrecip = hourly.getJSONArray("precipitation_probability")
        // Match on the hour prefix ("yyyy-MM-ddTHH") so the current hour is included
        // rather than skipped to the next one.
        val nowHour = current.getString("time").take(13)
        val startIdx = (0 until hourTimes.length()).firstOrNull { hourTimes.getString(it).take(13) >= nowHour } ?: 0

        val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        val currentWeather = CurrentWeather(
            temperature = current.getDouble("temperature_2m"),
            apparentTemperature = current.getDouble("apparent_temperature"),
            condition = WeatherCondition.fromCode(current.getInt("weather_code")),
            isDay = current.getInt("is_day") == 1,
            humidityPercent = current.getInt("relative_humidity_2m"),
            precipitationProbability = hourPrecip.optInt(startIdx, 0),
            windSpeed = current.getDouble("wind_speed_10m"),
            windDirectionDeg = current.getDouble("wind_direction_10m").toInt(),
            todayHigh = dailyMax.getDouble(0),
            todayLow = dailyMin.getDouble(0),
            sunrise = dailySunrise.getString(0).localTimeLabel(timeFmt),
            sunset = dailySunset.getString(0).localTimeLabel(timeFmt),
        )

        // Hourly: show the next 24 hours starting from the current hour.
        val endIdx = minOf(startIdx + 24, hourTimes.length())
        val hourFmt = DateTimeFormatter.ofPattern("h a", Locale.US)
        val hourlyList = (startIdx until endIdx).map { i ->
            HourForecast(
                hourLabel = hourTimes.getString(i).localTimeLabel(hourFmt),
                temperature = hourTemps.getDouble(i),
                condition = WeatherCondition.fromCode(hourCodes.getInt(i)),
                isDay = hourIsDay.getInt(i) == 1,
                precipitationProbability = hourPrecip.optInt(i, 0),
            )
        }

        val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)
        val today = dailyTimes.getString(0)
        val dailyList = (0 until dailyTimes.length()).map { i ->
            val date = dailyTimes.getString(i)
            DayForecast(
                dayLabel = if (date == today) "Today" else LocalDate.parse(date).format(dayFmt),
                high = dailyMax.getDouble(i),
                low = dailyMin.getDouble(i),
                condition = WeatherCondition.fromCode(dailyCodes.getInt(i)),
                precipitationProbability = dailyPrecip.optInt(i, 0),
            )
        }

        return Forecast(place, unit, currentWeather, hourlyList, dailyList)
    }

    /** Parse an ISO-local datetime ("2026-07-10T15:00") and format its time part. */
    private fun String.localTimeLabel(fmt: DateTimeFormatter): String =
        runCatching { java.time.LocalDateTime.parse(this).toLocalTime().format(fmt) }
            .getOrDefault(substringAfter('T'))

    private fun httpGet(urlString: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw java.io.IOException("HTTP $code from Open-Meteo")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }
}
