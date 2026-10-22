plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vidfix.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vidfix.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("ci") {
            // Configured through environment variables in GitHub Actions.
            val ksPath = System.getenv("VIDFIX_KEYSTORE_FILE")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("VIDFIX_KEYSTORE_PASS") ?: "vidfix123"
                keyAlias = System.getenv("VIDFIX_KEY_ALIAS") ?: "vidfix"
                keyPassword = System.getenv("VIDFIX_KEY_PASS") ?: "vidfix123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("VIDFIX_KEYSTORE_FILE") != null) {
                signingConfigs.getByName("ci")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // FFmpegKit maintained drop-in replacement (same com.arthenica.ffmpegkit API).
    // Includes GPL encoders: libx264/x265 - required for H.264 encoding.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl:8.1.9")
}
