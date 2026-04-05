plugins {
    alias(libs.plugins.android.application)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "org.vestifeed"

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "org.vestifeed"
        minSdk = 34
        targetSdk = 36
        versionCode = 24
        versionName = "0.4.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }

        release {
            signingConfig = signingConfigs.getByName("debug")

            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    // Coroutines
    implementation(libs.kotlinx.coroutines)
    testImplementation(libs.kotlinx.coroutines.test)

    // AndroidX
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.sqlite.bundled.jvm)
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
    implementation(libs.coil)
    implementation(libs.coil.network)

    // Parsing
    implementation(libs.jsoup)
    implementation(libs.re2j)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    //androidTestImplementation(libs.androidx.test.espresso.core)
    //debugImplementation(libs.androidx.test.monitor)
}
