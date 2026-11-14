plugins {
    id("ameki.android.application")
    id("ameki.android.compose")
}

android {
    namespace = "dev.antomanc.ameki"

    defaultConfig {
        applicationId = "dev.antomanc.ameki"
        versionCode = 1
        versionName = "0.1.0"
    }

    androidResources {
        localeFilters += listOf("en", "it")
    }

    signingConfigs {
        create("release") {
            // CI injects these; local release builds fall back to the debug key.
            val storeFile = System.getenv("AMEKI_KEYSTORE")
            if (storeFile != null) {
                this.storeFile = file(storeFile)
                storePassword = System.getenv("AMEKI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AMEKI_KEY_ALIAS")
                keyPassword = System.getenv("AMEKI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("AMEKI_KEYSTORE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    dependenciesInfo {
        // F-Droid: no Google-encrypted dependency metadata blob in the APK.
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.ui)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.home)
    implementation(projects.feature.locations)
    implementation(projects.feature.settings)
    implementation(projects.widget)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose.material3.windowsize)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.work.runtime.ktx)
}
