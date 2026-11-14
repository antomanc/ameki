plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "nembo.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "nembo.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "nembo.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidApplication") {
            id = "nembo.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
    }
}
