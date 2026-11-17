plugins {
    id("ameki.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.antomanc.ameki.core.data"

    buildFeatures { buildConfig = true }

    defaultConfig {
        // The Google source is off in public builds until Google confirms its terms allow it
        // (docs/adr/0002). Personal builds: ./gradlew assembleRelease -Pameki.googleSource=true
        val google = providers.gradleProperty("ameki.googleSource").orNull?.toBoolean() ?: false
        buildConfigField("boolean", "GOOGLE_SOURCE_ENABLED", google.toString())
        // GitHub release notify-only (#14). F-Droid / store builds can turn this off later:
        // ./gradlew assembleRelease -Pameki.updateChecks=false
        val updateChecks = providers.gradleProperty("ameki.updateChecks").orNull?.toBoolean() ?: true
        buildConfigField("boolean", "UPDATE_CHECKS_ENABLED", updateChecks.toString())
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(projects.core.model)
    api(projects.source.api)
    implementation(projects.source.google)
    implementation(projects.source.openmeteo)
    implementation(libs.androidx.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.turbine)
    testImplementation(libs.work.testing)
    testImplementation(libs.ktor.client.mock)
}
