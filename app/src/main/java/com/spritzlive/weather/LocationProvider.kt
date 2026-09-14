package com.spritzlive.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Thin wrapper over FusedLocationProviderClient that fetches a single fresh fix.
 * Unlike the speedometer, weather only needs one location, not a continuous stream.
 */
class LocationProvider(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()) else null

    /**
     * Returns one current [Location], or null if it could not be obtained.
     * The caller must already hold a location permission.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? = suspendCancellableCoroutine { cont ->
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(60_000L)  // a fix from the last minute is fine for weather
            .build()

        client.getCurrentLocation(request, null)
            .addOnSuccessListener { location -> cont.resumeIfActive(location) }
            .addOnFailureListener { cont.resumeIfActive(null) }
            .addOnCanceledListener { cont.resumeIfActive(null) }
    }

    private fun CancellableContinuation<Location?>.resumeIfActive(value: Location?) {
        if (isActive) resume(value)
    }

    /**
     * Reverse-geocode a fix to a named [Place] using the on-device geocoder, or
     * null if no geocoder backend is present or it returns no usable name. The
     * caller layers an HTTP fallback and a coordinate label behind this.
     */
    suspend fun describe(location: Location): Place? = withContext(Dispatchers.IO) {
        val g = geocoder ?: return@withContext null
        val lat = location.latitude
        val lon = location.longitude
        runCatching {
            @Suppress("DEPRECATION")
            val address = g.getFromLocation(lat, lon, 1)?.firstOrNull() ?: return@runCatching null
            val city = address.locality
                ?: address.subAdminArea
                ?: address.adminArea
                ?: return@runCatching null
            Place(
                name = city,
                admin = address.adminArea?.takeIf { it != city },
                country = address.countryName,
                latitude = lat,
                longitude = lon,
            )
        }.getOrNull()
    }
}
