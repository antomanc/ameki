plugins {
    id("ameki.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.provider.api)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.ktor.client.mock)
}
