import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
    id("com.squareup.sqldelight")
    // https://github.com/google/ksp/releases
    id("com.google.devtools.ksp") version "1.9.23-1.0.20"
}

val signingPropertiesFile = rootProject.file("signing.properties")

android {
    namespace = "co.appreactor.news"
    compileSdk = 34

    defaultConfig {
        applicationId = "co.appreactor.news"
        minSdk = 29
        targetSdk = 34
        versionCode = 24
        versionName = "0.4.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        setProperty("archivesBaseName", "news-$versionName")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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

    sourceSets.all {
        kotlin.srcDir("build/generated/ksp/$name/kotlin")
    }
}

sqldelight {
    database("Db") {
        packageName = "db"
        schemaOutputDirectory = file("src/main/sqldelight/$packageName/schemas")
        dialect = "sqlite:3.25"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Simplifies non-blocking programming
    // https://github.com/Kotlin/kotlinx.coroutines/releases
    val coroutinesVer = "1.8.0"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVer")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVer")

    // KTX extensions provide concise, idiomatic Kotlin to Jetpack, Android platform, and other APIs
    // https://developer.android.com/kotlin/ktx/extensions-list#dependency_6
    implementation("androidx.core:core-ktx:1.12.0")

    // Helps to segment the app into multiple, independent screens that are hosted within an Activity
    // https://developer.android.com/jetpack/androidx/releases/fragment
    val fragmentVer = "1.6.2"
    implementation("androidx.fragment:fragment-ktx:$fragmentVer")
    debugImplementation("androidx.fragment:fragment-testing:$fragmentVer")

    // Simplifies in-app navigation, assumes single activity pattern
    // https://developer.android.com/jetpack/androidx/releases/navigation
    // TODO fix upgrade to 2.7.7, it breaks the bookmarks tab
    val navVer = "2.5.3"
    implementation("androidx.navigation:navigation-fragment-ktx:$navVer")
    implementation("androidx.navigation:navigation-ui-ktx:$navVer")

    // Background job scheduler
    // Used to fetch new data in background
    // https://developer.android.com/jetpack/androidx/releases/work
    val workVer = "2.9.0"
    implementation("androidx.work:work-runtime-ktx:$workVer")
    androidTestImplementation("androidx.work:work-testing:$workVer")

    // In-app browser, it's about 2x faster than calling an external browser
    // https://developer.android.com/jetpack/androidx/releases/browser
    implementation("androidx.browser:browser:1.8.0")

    // Provides lifecycle-aware coroutine scopes
    // https://developer.android.com/jetpack/androidx/releases/lifecycle
    val lifecycleVer = "2.7.0"
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVer")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVer")

    // Helps to keep view hierarchies flat
    // https://developer.android.com/jetpack/androidx/releases/constraintlayout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // List widget
    // https://developer.android.com/jetpack/androidx/releases/recyclerview
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Enables swipe to refresh pattern
    // https://developer.android.com/jetpack/androidx/releases/swiperefreshlayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Retrofit turns HTTP APIs into Java interfaces
    // Used to communicate with remote backends
    // https://github.com/square/retrofit/blob/master/CHANGELOG.md
    val retrofitVer = "2.11.0"
    implementation("com.squareup.retrofit2:retrofit:$retrofitVer")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVer")

    // Modern HTTP client
    // https://github.com/square/okhttp/blob/master/CHANGELOG.md
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")
    testImplementation("com.squareup.okhttp3:mockwebserver")

    // Bundle SQLite binaries
    // https://github.com/requery/sqlite-android/releases
    implementation("com.github.requery:sqlite-android:3.45.0")

    // SQLDelight generates typesafe kotlin APIs from SQL statements
    // https://github.com/cashapp/sqldelight/releases
    val sqlDelightVer = "1.5.5"
    implementation("com.squareup.sqldelight:coroutines-extensions:$sqlDelightVer")
    implementation("com.squareup.sqldelight:android-driver:$sqlDelightVer")
    testImplementation("com.squareup.sqldelight:sqlite-driver:$sqlDelightVer")

    // Dependency injection framework
    // https://github.com/InsertKoinIO/koin/tags
    val koinAnnotationsVer = "1.0.0"
    implementation("io.insert-koin:koin-android:3.5.0")
    implementation("io.insert-koin:koin-annotations:$koinAnnotationsVer")
    ksp("io.insert-koin:koin-ksp-compiler:$koinAnnotationsVer")

    // Material design components
    // https://github.com/material-components/material-components-android/releases
    // TODO fix upgrade to 1.11.0, it makes the bottom navigation panel ugly
    implementation("com.google.android.material:material:1.9.0")

    // Used to download, cache and display images
    // https://github.com/square/picasso/releases
    implementation("com.squareup.picasso:picasso:2.8")

    // Java HTML parser
    // Used to auto-discover feed links
    // https://github.com/jhy/jsoup/releases
    implementation("org.jsoup:jsoup:1.17.2")

    // Feed parser
    // Used in standalone mode
    // https://github.com/bubelov/feedk/releases
    implementation("co.appreactor:feedk:0.2.6")

    // Custom global exception handler
    // https://github.com/ACRA/acra/releases
    val acraVer = "5.11.3"
    implementation("ch.acra:acra-mail:$acraVer")
    implementation("ch.acra:acra-dialog:$acraVer")

    // Core test infrastructure
    // https://junit.org/junit4/
    testImplementation("junit:junit:4.13.2")

    // Mocking library, better to go easy on that
    // https://github.com/mockk/mockk/releases
    testImplementation("io.mockk:mockk:1.13.10")

    // Core test infrastructure
    // https://junit.org/junit4/
    androidTestImplementation("junit:junit:4.13.2")

    // An instrumentation that runs various types of test cases
    // https://developer.android.com/jetpack/androidx/releases/test
    androidTestImplementation("androidx.test:runner:1.5.2")

    // UI testing framework
    // https://developer.android.com/jetpack/androidx/releases/test
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // TODO remove when fixed upstream
    // https://github.com/android/android-test/issues/1589
    debugImplementation("androidx.test:monitor:1.6.1")
}
