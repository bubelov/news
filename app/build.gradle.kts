import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

val signingPropertiesFile = rootProject.file("signing.properties")

android {
    namespace = "co.appreactor.news"
    compileSdk = 36

    defaultConfig {
        applicationId = "co.appreactor.news"
        minSdk = 33
        targetSdk = 36
        versionCode = 24
        versionName = "0.4.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        //setProperty("archivesBaseName", "news-$versionName")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += "-opt-in=kotlinx.coroutines.DelicateCoroutinesApi"
    }

    signingConfigs {
        if (signingPropertiesFile.exists()) {
            create("release") {
                val signingProperties = Properties()
                signingProperties.load(FileInputStream(signingPropertiesFile))
                storeFile = File(signingProperties["releaseKeystoreFile"] as String)
                storePassword = signingProperties["releaseKeystorePassword"] as String
                keyAlias = signingProperties["releaseKeyAlias"] as String
                keyPassword = signingProperties["releaseKeyPassword"] as String
            }
        }

        create("selfSignedRelease") {
            storeFile = File(rootDir, "release.jks")
            storePassword = "news-android"
            keyAlias = "news-android"
            keyPassword = "news-android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }

        if (signingPropertiesFile.exists()) {
            release {
                val signingProperties = Properties()
                signingProperties.load(FileInputStream(signingPropertiesFile))
                signingConfig = signingConfigs.getByName("release")
            }
        }

        create("selfSignedRelease") {
            signingConfig = signingConfigs.getByName("selfSignedRelease")
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Coroutines
    implementation(libs.kotlinx.coroutines)
    //testImplementation(libs.kotlinx.coroutines.test)

    // AndroidX
    implementation(libs.androidx.fragment)
    debugImplementation(libs.androidx.fragment.testing)
    implementation(libs.androidx.work)
    androidTestImplementation(libs.androidx.work.testing)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)

    // UI
    implementation(libs.material)
    implementation(libs.picasso)

    // Parsing
    implementation(libs.jsoup)

    // Feed parser
    implementation(libs.feedk)

    // Crash reporting
    implementation(libs.acra.mail)
    implementation(libs.acra.dialog)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.test.monitor)
}
