buildscript {
    repositories {
        maven("https://repo.maven.apache.org/maven2/")
        google() // Direct URL is not supported by F-Droid
    }

    dependencies {
        // https://developer.android.com/build/releases/gradle-plugin
        classpath("com.android.tools.build:gradle:8.9.1")
        // https://github.com/JetBrains/kotlin/releases
        classpath(kotlin("gradle-plugin", version = "1.9.23"))
        // https://developer.android.com/jetpack/androidx/releases/navigation
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.7.7")
    }
}

allprojects {
    repositories {
        maven("https://repo.maven.apache.org/maven2/")
        google() // Direct URL is not supported by F-Droid
        maven("https://jitpack.io")
    }
}

task("clean") {
    delete(rootProject.buildDir)
}
