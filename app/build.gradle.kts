import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing credentials live in keystore.properties (git-ignored).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

// MapTiler base-map key lives in maps.properties (git-ignored). Absent → the app
// falls back to OpenStreetMap base tiles, so builds work without the key.
val mapsPropertiesFile = rootProject.file("maps.properties")
val mapsProperties = Properties().apply {
    if (mapsPropertiesFile.exists()) load(FileInputStream(mapsPropertiesFile))
}
val mapTilerKey = (mapsProperties["maptilerKey"] as String?).orEmpty()

android {
    namespace = "com.spritzlive.weather"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.spritzlive.weather"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
        vectorDrawables { useSupportLibrary = true }

        // Exposed to code as BuildConfig.MAPTILER_KEY; empty string when unset.
        buildConfigField("String", "MAPTILER_KEY", "\"$mapTilerKey\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only sign with the release key when the keystore is present, so the
            // build still works on machines without it (they produce an unsigned build).
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Google Play Services location — FusedLocationProvider (one-shot current fix).
    implementation("com.google.android.gms:play-services-location:21.4.0")

    // osmdroid — OpenStreetMap base tiles + RainViewer radar overlay. Key-less,
    // no Google Play Services dependency for the map itself.
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    testImplementation("junit:junit:4.13.2")
}
