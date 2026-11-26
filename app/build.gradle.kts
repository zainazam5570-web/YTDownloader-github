plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.ytdl"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ytdl"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Zyada tar naye phones arm64 hote hain; size kam rakhne ke liye sirf ye do
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // yt-dlp aur ffmpeg ki native files ke liye zaroori
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.17.4")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.4")
}
