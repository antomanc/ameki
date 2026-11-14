plugins {
    id("nembo.android.library")
    id("nembo.android.compose")
}

android {
    namespace = "dev.antomanc.nembo.core.ui"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
    api(libs.compose.material3)
    api(libs.compose.animation)
}
