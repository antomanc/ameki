enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ameki"

include(
    ":app",
    ":core:model",
    ":core:data",
    ":core:ui",
    ":source:api",
    ":source:google",
    ":source:openmeteo",
    ":feature:onboarding",
    ":feature:home",
    ":feature:locations",
    ":feature:settings",
    ":widget",
)
