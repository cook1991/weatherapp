package com.spritzlive.weather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spritzlive.weather.CurrentWeather
import com.spritzlive.weather.DayForecast
import com.spritzlive.weather.Forecast
import com.spritzlive.weather.HourForecast
import com.spritzlive.weather.Place
import com.spritzlive.weather.RadarData
import com.spritzlive.weather.WeatherUiState
import com.spritzlive.weather.windCompass
import kotlin.math.roundToInt

@Composable
fun WeatherScreen(
    state: WeatherUiState,
    deniedPermanently: Boolean,
    onUseLocation: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectPlace: (Place) -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggleUnit: () -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        SearchBar(
            unitDegree = state.unit.degree,
            onSearch = onSearch,
            onClearSearch = onClearSearch,
            onToggleUnit = onToggleUnit,
            onUseLocation = onUseLocation,
        )

        if (state.searchResults.isNotEmpty()) {
            SearchResults(state.searchResults, onSelectPlace)
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.forecast != null -> ForecastContent(
                    forecast = state.forecast,
                    radar = state.radar,
                    isLoading = state.isLoading,
                    error = state.error,
                    onRefresh = onRefresh,
                    onDismissError = onDismissError,
                )
                state.isLoading -> CenteredSpinner()
                state.error != null -> ErrorContent(state.error, onUseLocation, onDismissError)
                else -> WelcomeContent(deniedPermanently, onUseLocation, onOpenSettings)
            }
        }

        SupportLink(onSupport)
    }
}

@Composable
private fun SearchBar(
    unitDegree: String,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggleUnit: () -> Unit,
    onUseLocation: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSearch(it)
            },
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Search city") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        onClearSearch()
                    }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )
        // Unit toggle (°C / °F).
        OutlinedButton(onClick = onToggleUnit) {
            Text(unitDegree, fontWeight = FontWeight.Bold)
        }
        // Use-my-location button.
        IconButton(onClick = {
            query = ""
            onClearSearch()
            onUseLocation()
        }) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = "Use my location",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SearchResults(results: List<Place>, onSelectPlace: (Place) -> Unit) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
            items(results) { place ->
                Text(
                    text = place.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPlace(place) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                HorizontalDivider(color = Track)
            }
        }
    }
}

@Composable
private fun ForecastContent(
    forecast: Forecast,
    radar: RadarData?,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Place + refresh
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = forecast.place.label,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Muted)
                }
            }
        }

        if (error != null) {
            ErrorBanner(error, onDismissError)
        }

        CurrentBlock(forecast.current, forecast.unit.degree)

        Spacer(Modifier.height(8.dp))
        StatsRow(forecast.current, forecast.unit.windLabel)

        Spacer(Modifier.height(16.dp))
        SunRow(forecast.current)

        Spacer(Modifier.height(20.dp))
        HourlyStrip(forecast.hourly, forecast.unit.degree)

        Spacer(Modifier.height(20.dp))
        RadarCard(
            latitude = forecast.place.latitude,
            longitude = forecast.place.longitude,
            placeLabel = forecast.place.label,
            radar = radar,
        )

        Spacer(Modifier.height(20.dp))
        DailyList(forecast.daily, forecast.unit.degree)

        Spacer(Modifier.height(12.dp))
    }
}

/** The big current-temperature readout sitting inside the temperature dial. */
@Composable
private fun CurrentBlock(current: CurrentWeather, degree: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        TempDial(
            current = current.temperature.toFloat(),
            low = current.todayLow.toFloat(),
            high = current.todayHigh.toFloat(),
            degree = degree,
            cool = MaterialTheme.colorScheme.primary,
            warm = MaterialTheme.colorScheme.secondary,
            trackColor = Track,
            modifier = Modifier.fillMaxWidth(0.86f),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = current.condition.emoji, fontSize = 44.sp)
            Text(
                text = "${current.temperature.roundToInt()}$degree",
                fontSize = 84.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = current.condition.description,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Feels like ${current.apparentTemperature.roundToInt()}$degree",
                fontSize = 14.sp,
                color = Muted,
            )
        }
    }
}

@Composable
private fun StatsRow(current: CurrentWeather, windLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Stat("HUMIDITY", "${current.humidityPercent}", "%")
        Stat("PRECIP", "${current.precipitationProbability}", "% chance")
        Stat(
            "WIND",
            "${current.windSpeed.roundToInt()}",
            "$windLabel ${windCompass(current.windDirectionDeg)}",
        )
    }
}

@Composable
private fun SunRow(current: CurrentWeather) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SunStat("🌅", "SUNRISE", current.sunrise)
        SunStat("🌇", "SUNSET", current.sunset)
    }
}

@Composable
private fun SunStat(emoji: String, label: String, time: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(emoji, fontSize = 26.sp)
        Column(horizontalAlignment = Alignment.Start) {
            Text(label, fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Bold)
            Text(
                time,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(unit, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun HourlyStrip(hourly: List<HourForecast>, degree: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("NEXT 24 HOURS")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            items(hourly) { hour ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(hour.hourLabel, fontSize = 13.sp, color = Muted)
                    Text(hour.condition.emoji, fontSize = 24.sp)
                    Text(
                        "${hour.temperature.roundToInt()}$degree",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    // Reserve the line even at 0% so all columns stay the same height.
                    Text(
                        text = if (hour.precipitationProbability > 0) "💧${hour.precipitationProbability}%" else "",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyList(daily: List<DayForecast>, degree: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("7-DAY FORECAST")
        Spacer(Modifier.height(4.dp))
        daily.forEach { day ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    day.dayLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (day.precipitationProbability > 0) {
                    Text(
                        "💧${day.precipitationProbability}%",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(day.condition.emoji, fontSize = 22.sp)
                Spacer(Modifier.width(16.dp))
                Text(
                    "${day.high.roundToInt()}$degree",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${day.low.roundToInt()}$degree",
                    fontSize = 16.sp,
                    color = Muted,
                )
            }
            HorizontalDivider(color = Track)
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Muted,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun WelcomeContent(
    deniedPermanently: Boolean,
    onUseLocation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(64.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Weather, anywhere",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Use your location for local conditions, or search for any city above. " +
                "Data comes from Open-Meteo — no account, no tracking.",
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            color = Muted,
        )
        Spacer(Modifier.height(24.dp))
        if (deniedPermanently) {
            Text(
                "Location permission was denied. Enable it in Settings, or just search for a city.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
        } else {
            OutlinedButton(onClick = onUseLocation) {
                Icon(Icons.Filled.LocationOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Use my location")
            }
        }
    }
}

@Composable
private fun ErrorContent(error: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            error,
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = { onDismiss(); onRetry() }) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Try again")
        }
    }
}

@Composable
private fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                error,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Clear, contentDescription = "Dismiss", tint = Muted)
            }
        }
    }
}

@Composable
private fun SupportLink(onSupport: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onSupport) {
            Text("☕  Buy me a coffee", fontSize = 14.sp, color = Color(0xFF8899AA))
        }
    }
}
