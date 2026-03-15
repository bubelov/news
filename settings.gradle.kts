pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "9.0.1"
        id("org.jetbrains.kotlin.android") version "2.2.10"
        id("org.jetbrains.kotlin.plugin.parcelize") version "2.0.21"
        id("androidx.navigation.safeargs.kotlin") version "2.9.6"
        id("com.google.devtools.ksp") version "2.3.2"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

include(":app")
