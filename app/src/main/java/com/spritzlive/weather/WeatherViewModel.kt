package com.spritzlive.weather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything the UI needs to render the weather screen. */
data class WeatherUiState(
    val unit: TempUnit = TempUnit.FAHRENHEIT,
    val forecast: Forecast? = null,
    /** Animatable precipitation-radar frames from RainViewer, or null if unavailable. */
    val radar: RadarData? = null,
    /** True while a forecast or location fetch is in flight. */
    val isLoading: Boolean = false,
    /** A user-facing error message, or null. */
    val error: String? = null,
    /** Live results for the city-search box. */
    val searchResults: List<Place> = emptyList(),
    val isSearching: Boolean = false,
    /** True before the very first place has been chosen (shows the welcome prompt). */
    val needsLocation: Boolean = false,
)

class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WeatherRepository()
    private val locationProvider = LocationProvider(app)
    private val prefs = WeatherPrefs(app)

    private val _state = MutableStateFlow(WeatherUiState(unit = prefs.unit))
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        val saved = prefs.lastPlace
        if (saved != null) {
            load(saved)
        } else {
            _state.value = _state.value.copy(needsLocation = true)
        }
    }

    /** Fetch a fresh GPS fix and load weather for wherever the device is. */
    fun useCurrentLocation() {
        loadJob?.cancel()
        _state.value = _state.value.copy(isLoading = true, error = null, needsLocation = false)
        loadJob = viewModelScope.launch {
            try {
                val loc = locationProvider.currentLocation()
                    ?: throw IllegalStateException("Couldn't get your location. Try again or search for a city.")
                // Name the fix: on-device geocoder → BigDataCloud HTTP → raw coordinates.
                val place = locationProvider.describe(loc)
                    ?: repo.reverseGeocode(loc.latitude, loc.longitude)
                    ?: Place(
                        name = "%.3f, %.3f".format(java.util.Locale.US, loc.latitude, loc.longitude),
                        admin = null, country = null,
                        latitude = loc.latitude, longitude = loc.longitude,
                    )
                fetchInto(place)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.friendlyMessage())
            }
        }
    }

    /** Load weather for an explicitly chosen [place] (from search or persistence). */
    fun load(place: Place) {
        loadJob?.cancel()
        _state.value = _state.value.copy(
            isLoading = true, error = null, needsLocation = false, searchResults = emptyList(),
        )
        loadJob = viewModelScope.launch {
            try {
                fetchInto(place)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.friendlyMessage())
            }
        }
    }

    /** Re-fetch the currently shown place. */
    fun refresh() {
        _state.value.forecast?.place?.let { load(it) }
    }

    private suspend fun fetchInto(place: Place) {
        val forecast = repo.forecast(place, _state.value.unit)
        prefs.lastPlace = place
        // Radar is a best-effort extra (repo.radar() returns null on failure), so it
        // never blocks the forecast. Reuse the previous frames if this fetch yields none.
        val radar = repo.radar() ?: _state.value.radar
        _state.value = _state.value.copy(
            isLoading = false, error = null, forecast = forecast, radar = radar,
        )
    }

    fun toggleUnit() {
        val next = _state.value.unit.other()
        prefs.unit = next
        _state.value = _state.value.copy(unit = next)
        // Re-fetch in the new unit (Open-Meteo converts server-side).
        _state.value.forecast?.place?.let { load(it) }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        _state.value = _state.value.copy(isSearching = true)
        searchJob = viewModelScope.launch {
            try {
                val results = repo.search(query)
                _state.value = _state.value.copy(searchResults = results, isSearching = false)
            } catch (_: Exception) {
                _state.value = _state.value.copy(searchResults = emptyList(), isSearching = false)
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.value = _state.value.copy(searchResults = emptyList(), isSearching = false)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun Exception.friendlyMessage(): String = when (this) {
        is java.net.UnknownHostException -> "No internet connection."
        is java.net.SocketTimeoutException -> "The weather service timed out. Try again."
        else -> message ?: "Something went wrong fetching the weather."
    }
}
