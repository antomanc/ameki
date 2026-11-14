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
}
