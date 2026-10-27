pluginManagement {
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

rootProject.name = "Einkaufsliste"

// Pure Kotlin domain logic lives in its own build so it can be compiled and
// tested without the Android SDK (see docs/ARCHITECTURE.md).
includeBuild("core")
include(":app")
