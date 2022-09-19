buildscript {
    repositories {
        maven("https://repo.maven.apache.org/maven2/")
        google() // Direct URL is not supported by F-Droid
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.3.0")
        classpath(kotlin("gradle-plugin", version = "1.6.21"))
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.5.2")
        classpath("com.squareup.sqldelight:gradle-plugin:1.5.3")
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
