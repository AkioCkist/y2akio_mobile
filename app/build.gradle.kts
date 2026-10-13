plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.y2akio_mobile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.y2akio_mobile"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

    }

    buildTypes {
        release {
            // yt-dlp's Python runtime is loaded reflectively by youtubedl-android; keep shrinking off.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // One APK per ABI (the bundled Python/FFmpeg make a universal APK ~3x larger).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        // youtubedl-android runs python/ffmpeg from the native library dir: libs must be extracted.
        jniLibs.useLegacyPackaging = true
        // The *.zip.so payloads are archives, not ELF files — nothing to strip.
        jniLibs.keepDebugSymbols += "**/*.zip.so"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.documentfile)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    // Android's org.json is a stub in local unit tests; use the real implementation there.
    testImplementation(libs.org.json)
}
