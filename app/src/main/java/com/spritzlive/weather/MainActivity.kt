package com.spritzlive.weather

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spritzlive.weather.ui.WeatherScreen
import com.spritzlive.weather.ui.WeatherTheme

private const val SUPPORT_URL = "https://buymeacoffee.com/spritzjason"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeatherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WeatherApp()
                }
            }
        }
    }
}

private fun hasLocationPermission(activity: ComponentActivity): Boolean =
    ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun WeatherApp() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val vm: WeatherViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    var deniedPermanently by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.useCurrentLocation()
        } else {
            deniedPermanently =
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    // Ask for the current location: use it directly if permitted, otherwise request.
    val requestLocation: () -> Unit = {
        if (hasLocationPermission(activity)) {
            vm.useCurrentLocation()
        } else {
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    WeatherScreen(
        state = state,
        deniedPermanently = deniedPermanently,
        onUseLocation = requestLocation,
        onOpenSettings = {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
            }
            context.startActivity(intent)
        },
        onSelectPlace = vm::load,
        onSearch = vm::search,
        onClearSearch = vm::clearSearch,
        onToggleUnit = vm::toggleUnit,
        onRefresh = vm::refresh,
        onDismissError = vm::dismissError,
        onSupport = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        },
    )
}
