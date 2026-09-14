package com.spritzlive.weather.ui

import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spritzlive.weather.BuildConfig
import com.spritzlive.weather.RadarData
import com.spritzlive.weather.RadarFrame
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// RainViewer tile params: 256px tiles, colour scheme 2 ("Universal Blue", light→dark
// blue for light→heavy precip), with smoothing + snow layers on ("1_1").
// NOTE: the free/key-less public API serves Universal Blue only — the other schemes
// (e.g. 4 = The Weather Channel) require RainViewer's paid, keyed Tiles API, so this
// stays at 2 to match what the endpoint actually renders.
private const val RADAR_TILE_SIZE = 256
private const val RADAR_COLOR_SCHEME = 2
private const val RADAR_OPTIONS = "1_1"
// MapTiler base-map style id. "streets-v2-dark" has bolder, whiter place labels than
// the muted "dataviz-dark". Swap this to a custom style id for full label control.
private const val MAPTILER_STYLE = "streets-v2-dark"
// Zoom range. RainViewer's free radar tiles only exist up to z7. The map max is
// held at that level so the radar always has native tiles: zooming past z7 would
// force osmdroid to fake the overlay by scaling the z7 tile, which blinks in and
// out during a pinch. Capping at RADAR_MAX_ZOOM keeps the radar rock-solid (the
// pinch just stops smoothly at the max instead of flickering).
private const val MIN_ZOOM = 3.0
private const val RADAR_MAX_ZOOM = 7        // RainViewer's top radar zoom
private const val MAX_ZOOM = 7.0            // = RADAR_MAX_ZOOM, so radar never overzooms
private const val DEFAULT_ZOOM = 7.0
private const val FRAME_MILLIS = 900L
private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
// Approximates RainViewer's "Universal Blue" ramp (light→heavy precip) for the legend.
private val RADAR_LEGEND_COLORS = listOf(
    Color(0xFFB3E0FF), Color(0xFF4FA3F7), Color(0xFF1560C0),
    Color(0xFF00E0C0), Color(0xFFFFE93B), Color(0xFFFF8A34), Color(0xFFE53935),
)

/**
 * Inline animated precipitation-radar card: a dark base map centered on the current
 * place with RainViewer radar overlaid, looped through the past ~2h + short nowcast.
 * A full-screen button opens the same map + controls in a full-screen dialog.
 * Renders a graceful placeholder when radar data couldn't be fetched.
 */
@Composable
fun RadarCard(
    latitude: Double,
    longitude: Double,
    placeLabel: String,
    radar: RadarData?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel("RADAR")
        Spacer(Modifier.height(8.dp))
        if (radar == null || radar.frames.isEmpty()) {
            RadarUnavailable()
        } else {
            var showFullScreen by remember { mutableStateOf(false) }
            RadarPlayer(
                latitude, longitude, placeLabel, radar,
                fullScreen = false,
                onToggleFullScreen = { showFullScreen = true },
            )
            if (showFullScreen) {
                Dialog(
                    onDismissRequest = { showFullScreen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    RadarPlayer(
                        latitude, longitude, placeLabel, radar,
                        fullScreen = true,
                        onToggleFullScreen = { showFullScreen = false },
                    )
                }
            }
        }
    }
}

/**
 * The map + transport controls. [fullScreen] switches between the inline card
 * (fixed height) and a screen-filling layout; [onToggleFullScreen] enters or exits
 * full screen. The map is a classic osmdroid [MapView] bridged in via [AndroidView];
 * each call owns its own MapView, so the inline and dialog instances are independent.
 */
@Composable
private fun RadarPlayer(
    latitude: Double,
    longitude: Double,
    placeLabel: String,
    radar: RadarData,
    fullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
) {
    val ctx = LocalContext.current
    val frames = radar.frames
    val holder = remember { RadarMapHolder() }

    // Frame cursor + play state reset whenever a fresh radar set arrives. Start
    // PAUSED on the current-time frame: one screen of tiles instead of a request
    // storm, and the card is useful immediately.
    var index by remember(radar) { mutableIntStateOf(radar.nowIndex) }
    var playing by remember(radar) { mutableStateOf(false) }

    // Fade the map in on mount so the initial tile load (esp. the fresh full-screen
    // map) happens behind the fade instead of flashing base map → radar pop-in.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val mapAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "radarFade",
    )

    // One-time osmdroid config: identify ourselves per the OSM/MapTiler tile policy,
    // keep the tile cache in the app's private dir (no storage permission), and cap
    // concurrent downloads so a play-through paces itself under RainViewer's limit.
    remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            val base = File(ctx.cacheDir, "osmdroid")
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
            tileDownloadThreads = 2.toShort()
            tileDownloadMaxQueueSize = 12.toShort()
        }
        true
    }

    LaunchedEffect(playing, radar) {
        if (playing) {
            while (true) {
                delay(FRAME_MILLIS)
                index = (index + 1) % frames.size
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { holder.map?.onDetach() }
    }

    // Our per-frame radar overlays use custom tile providers that don't repaint the
    // map when their tiles finish loading — so without help the radar stayed blank
    // until a touch forced a redraw (on mount, on Play, after scrubbing). Drive the
    // repaints ourselves: continuously while playing, and for ~1.5s after any frame
    // change or (re)mount, so late-arriving tiles always draw on their own.
    LaunchedEffect(playing, index, radar) {
        var nudges = 0
        while (playing || nudges < 10) {
            holder.map?.invalidate()
            delay(150)
            nudges++
        }
    }

    Column(
        modifier = if (fullScreen) {
            Modifier.fillMaxSize().background(DarkBg).padding(12.dp)
        } else {
            Modifier.fillMaxWidth()
        },
    ) {
        Box(
            modifier = (
                if (fullScreen) Modifier.fillMaxWidth().weight(1f)
                else Modifier.fillMaxWidth().height(260.dp)
                ).clip(RoundedCornerShape(14.dp)).alpha(mapAlpha),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    MapView(c).apply {
                        setTileSource(baseMapSource())
                        setMultiTouchControls(true)
                        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                        minZoomLevel = MIN_ZOOM
                        maxZoomLevel = MAX_ZOOM
                        controller.setZoom(DEFAULT_ZOOM)
                        controller.setCenter(GeoPoint(latitude, longitude))
                        overlays.add(
                            Marker(this).apply {
                                position = GeoPoint(latitude, longitude)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = placeLabel
                            }.also { holder.marker = it },
                        )
                        // Claim touches so panning/zooming isn't stolen by the
                        // enclosing scroll. Returns false so the map still handles it.
                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
                        holder.map = this
                        holder.centeredLat = latitude
                        holder.centeredLon = longitude
                        onResume()
                    }
                },
                update = { map ->
                    if (holder.centeredLat != latitude || holder.centeredLon != longitude) {
                        map.controller.setCenter(GeoPoint(latitude, longitude))
                        holder.marker?.apply {
                            position = GeoPoint(latitude, longitude)
                            title = placeLabel // keep the marker's label in sync with the place
                            closeInfoWindow()
                        }
                        holder.centeredLat = latitude
                        holder.centeredLon = longitude
                    }
                    // Radar animation = one overlay per frame, switched by visibility.
                    // (Earlier we swapped a single overlay's tile source per frame, but
                    // osmdroid's setTileSource() clears the tile cache — leaving a
                    // transparent gap before the next frame/zoom reloaded, which read
                    // as the radar blinking "in and out".) Pre-building an overlay per
                    // frame and just toggling isEnabled never clears the cache: frame
                    // switches are instant and tiles survive zoom/pan. Overlays are
                    // created disabled, so a frame only fetches its tiles once it's
                    // first shown — no upfront request burst.
                    // Rebuild the frame overlays if the radar data itself changed
                    // (new city / refreshed frame list), so they never go stale.
                    if (holder.radar !== radar) {
                        holder.frameOverlays?.forEach { map.overlays.remove(it) }
                        holder.frameOverlays = null
                        holder.shownIndex = -1
                        holder.radar = radar
                    }
                    var frameOverlays = holder.frameOverlays
                    if (frameOverlays == null) {
                        frameOverlays = frames.map { frame ->
                            val p = MapTileProviderBasic(map.context, rainViewerTileSource(radar, frame))
                            TilesOverlay(p, map.context).apply {
                                loadingBackgroundColor = AndroidColor.TRANSPARENT
                                isEnabled = false
                            }
                        }
                        // Insert beneath the place marker (added first in the factory).
                        frameOverlays.forEachIndexed { i, ov -> map.overlays.add(i, ov) }
                        holder.frameOverlays = frameOverlays
                        holder.shownIndex = -1
                    }
                    if (index != holder.shownIndex) {
                        frameOverlays.getOrNull(holder.shownIndex)?.isEnabled = false
                        frameOverlays[index].isEnabled = true
                        holder.shownIndex = index
                        map.invalidate()
                    }
                },
            )

            // Timestamp chip (top-right).
            Surface(
                color = DarkSurface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Text(
                    text = frameLabel(frames[index]),
                    color = if (frames[index].isForecast) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        // Transport controls: play/pause + scrubbable timeline.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { playing = !playing }) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = index.toFloat(),
                onValueChange = {
                    playing = false
                    index = it.roundToInt().coerceIn(0, frames.size - 1)
                },
                valueRange = 0f..(frames.size - 1).toFloat(),
                steps = (frames.size - 2).coerceAtLeast(0),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = frameLabel(frames[index]),
                fontSize = 13.sp,
                color = Muted,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 84.dp),
            )
            IconButton(onClick = onToggleFullScreen) {
                Icon(
                    imageVector = if (fullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (fullScreen) "Exit full screen" else "Full screen",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        RadarLegend()

        Text(
            text = baseMapAttribution(),
            fontSize = 10.sp,
            color = Muted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Precipitation-intensity colour scale (light → heavy), matching the radar palette. */
@Composable
private fun RadarLegend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Light", fontSize = 10.sp, color = Muted)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(RADAR_LEGEND_COLORS)),
        )
        Text("Heavy", fontSize = 10.sp, color = Muted)
    }
}

@Composable
private fun RadarUnavailable() {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().height(120.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Text("Radar is temporarily unavailable.", color = Muted, fontSize = 14.sp)
        }
    }
}

/**
 * Base map under the radar. When a MapTiler key is configured (maps.properties →
 * BuildConfig.MAPTILER_KEY) this is the MapTiler dark style named by [MAPTILER_STYLE]
 * — a dark canvas that reads well beneath coloured radar and is licensed for
 * production use. Without a key it falls back to OpenStreetMap's Mapnik tiles (fine
 * for local/dev builds, but not sanctioned for a published app — see README).
 */
private fun baseMapSource(): OnlineTileSourceBase {
    val key = BuildConfig.MAPTILER_KEY
    if (key.isBlank()) return TileSourceFactory.MAPNIK
    return object : OnlineTileSourceBase(
        "MapTiler-$MAPTILER_STYLE", 0, 20, RADAR_TILE_SIZE, ".png",
        arrayOf("https://api.maptiler.com/maps/$MAPTILER_STYLE/"),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "https://api.maptiler.com/maps/$MAPTILER_STYLE/$RADAR_TILE_SIZE/$z/$x/$y.png?key=$key"
        }
    }
}

/** Attribution line, matching whichever base map is actually in use. */
private fun baseMapAttribution(): String =
    if (BuildConfig.MAPTILER_KEY.isNotBlank()) {
        "© MapTiler © OpenStreetMap contributors · Radar by RainViewer"
    } else {
        "© OpenStreetMap contributors · Radar by RainViewer"
    }

/** e.g. "2:40 PM", with a tag on the short-range forecast frames. */
private fun frameLabel(frame: RadarFrame): String {
    val time = Instant.ofEpochSecond(frame.timestampSec)
        .atZone(ZoneId.systemDefault())
        .format(TIME_FMT)
    return if (frame.isForecast) "$time · forecast" else time
}

/**
 * A one-frame RainViewer tile source. Each frame gets a distinct source name (its
 * API path) so osmdroid caches frames separately and the loop replays from cache
 * after the first pass. URL shape: {host}{path}/256/{z}/{x}/{y}/{colour}/{opts}.png
 */
private fun rainViewerTileSource(radar: RadarData, frame: RadarFrame): OnlineTileSourceBase {
    val base = radar.tileUrlBase(frame)
    return object : OnlineTileSourceBase(
        frame.path, 0, RADAR_MAX_ZOOM, RADAR_TILE_SIZE, ".png", arrayOf(base),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$base/$RADAR_TILE_SIZE/$z/$x/$y/$RADAR_COLOR_SCHEME/$RADAR_OPTIONS.png"
        }
    }
}

/** Holds the imperative osmdroid handles across recompositions. */
private class RadarMapHolder {
    var map: MapView? = null
    /** The radar data the current [frameOverlays] were built from. */
    var radar: RadarData? = null
    /** One tile overlay per radar frame; only the shown one is enabled at a time. */
    var frameOverlays: List<TilesOverlay>? = null
    var marker: Marker? = null
    var centeredLat: Double = Double.NaN
    var centeredLon: Double = Double.NaN
    /** Frame index currently visible; -1 until the first is shown. */
    var shownIndex: Int = -1
}
