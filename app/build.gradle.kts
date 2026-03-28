// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application)   // ← QUESTA RIGA sblocca "android { }"
    alias(libs.plugins.kotlin.android)
}

// Nel tuo build.gradle (Module: app) aggiungi questo blocco dentro android { }

android {
    namespace = "com.everygo.musicplayer"
    compileSdk = 36         // ← aggiungi questa riga

    defaultConfig {
        applicationId = "com.everygo.musicplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.media)
    implementation(libs.jaudiotagger)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.material)
}
