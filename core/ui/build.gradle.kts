plugins {
    id("ameki.android.library")
    id("ameki.android.compose")
}

android {
    namespace = "dev.antomanc.ameki.core.ui"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
    api(libs.compose.material3)
    api(libs.compose.animation)
    api(libs.lottie.compose)

    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
