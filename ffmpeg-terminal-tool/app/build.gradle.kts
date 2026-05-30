plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdk = 33

    defaultConfig {
        applicationId = "com.example.ffmpegterm"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        // Enable multidex support if needed
        // multiDexEnabled = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Configure the application to use the assets directory for FFmpeg binaries
    sourceSets {
        getByName("main").assets.srcDirs += file("src/main/assets")
    }

    // Configure Kotlin options
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")
    // Add other dependencies as needed
}