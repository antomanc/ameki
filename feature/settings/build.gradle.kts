plugins {
    id("nembo.android.library")
    id("nembo.android.compose")
}

android {
    namespace = "dev.antomanc.nembo.feature.settings"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.koin.androidx.compose)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.windowsize)

    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
