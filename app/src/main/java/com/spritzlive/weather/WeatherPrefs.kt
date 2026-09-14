package com.spritzlive.weather

import android.content.Context

/** SharedPreferences wrapper for the unit setting and the last-viewed place. */
class WeatherPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    var unit: TempUnit
        get() = runCatching { TempUnit.valueOf(prefs.getString(KEY_UNIT, TempUnit.FAHRENHEIT.name)!!) }
            .getOrDefault(TempUnit.FAHRENHEIT)
        set(value) = prefs.edit().putString(KEY_UNIT, value.name).apply()

    /**
     * The last place the user viewed, so the app opens straight to it next launch
     * instead of re-prompting for location. Null until something has been chosen.
     */
    var lastPlace: Place?
        get() {
            val name = prefs.getString(KEY_NAME, null) ?: return null
            if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) return null
            return Place(
                name = name,
                admin = prefs.getString(KEY_ADMIN, null),
                country = prefs.getString(KEY_COUNTRY, null),
                latitude = prefs.getFloat(KEY_LAT, 0f).toDouble(),
                longitude = prefs.getFloat(KEY_LON, 0f).toDouble(),
            )
        }
        set(value) {
            val e = prefs.edit()
            if (value == null) {
                e.remove(KEY_NAME).remove(KEY_ADMIN).remove(KEY_COUNTRY).remove(KEY_LAT).remove(KEY_LON)
            } else {
                e.putString(KEY_NAME, value.name)
                    .putString(KEY_ADMIN, value.admin)
                    .putString(KEY_COUNTRY, value.country)
                    .putFloat(KEY_LAT, value.latitude.toFloat())
                    .putFloat(KEY_LON, value.longitude.toFloat())
            }
            e.apply()
        }

    private companion object {
        const val KEY_UNIT = "unit"
        const val KEY_NAME = "place_name"
        const val KEY_ADMIN = "place_admin"
        const val KEY_COUNTRY = "place_country"
        const val KEY_LAT = "place_lat"
        const val KEY_LON = "place_lon"
    }
}
