plugins {
    alias(libs.plugins.android.application)
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "co.appreactor.news"

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "co.appreactor.news"
        minSdk = 33
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
            // https://developer.android.com/topic/performance/app-optimization/enable-app-optimization

            // Enables code-related app optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true

            // Includes the default ProGuard rules file
            proguardFiles(
                // Default file with automatically generated optimization rules.
                getDefaultProguardFile("proguard-android-optimize.txt"),
            )

            signingConfig = signingConfigs.getByName("debug")
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

    // Feed parser
    implementation(libs.feedk)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    //androidTestImplementation(libs.androidx.test.espresso.core)
    //debugImplementation(libs.androidx.test.monitor)
}
