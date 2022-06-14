import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
    id("com.squareup.sqldelight")
    id("com.google.devtools.ksp") version "1.6.21-1.0.5"
}

val signingPropertiesFile = rootProject.file("signing.properties")

android {
    compileSdk = 31

    defaultConfig {
        applicationId = "co.appreactor.news"
        minSdk = 26
        targetSdk = 31
        versionCode = 20
        versionName = "0.3.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        setProperty("archivesBaseName", "news-$versionName")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    signingConfigs {
        if (signingPropertiesFile.exists()) {
            create("release") {
                val signingProperties = Properties()
                signingProperties.load(FileInputStream(signingPropertiesFile))
                storeFile = File(signingProperties["playKeystoreFile"] as String)
                storePassword = signingProperties["playKeystorePassword"] as String
                keyAlias = signingProperties["playKeyAlias"] as String
                keyPassword = signingProperties["playKeyPassword"] as String
            }
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
    }

    buildFeatures {
        viewBinding = true
    }

    sourceSets.all {
        kotlin.srcDir("build/generated/ksp/$name/kotlin")
    }
}

sqldelight {
    database("Database") {
        sourceFolders = listOf("sqldelight")
        packageName = "db"
        deriveSchemaFromMigrations = true
    }
}

dependencies {
    // Simplifies non-blocking programming
    val coroutinesVer = "1.6.2"
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVer")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVer")

    // KTX extensions provide concise, idiomatic Kotlin to Jetpack, Android platform, and other APIs
    implementation("androidx.core:core-ktx:1.8.0")

    val fragmentVer = "1.4.1"
    implementation("androidx.fragment:fragment-ktx:$fragmentVer")
    debugImplementation("androidx.fragment:fragment-testing:$fragmentVer")

    // Simplifies in-app navigation, assumes single activity pattern
    val navVer = "2.4.2"
    implementation("androidx.navigation:navigation-fragment-ktx:$navVer")
    implementation("androidx.navigation:navigation-ui-ktx:$navVer")

    // Background job scheduler
    // Used to fetch new data in background
    val workVer = "2.7.1"
    implementation("androidx.work:work-runtime-ktx:$workVer")
    androidTestImplementation("androidx.work:work-testing:$workVer")

    // In-app browser, it's about 2x faster than calling an external browser
    implementation("androidx.browser:browser:1.4.0")

    // Provides lifecycle-aware coroutine scopes
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.1")

    // Helps to keep view hierarchies flat
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // List widget
    implementation("androidx.recyclerview:recyclerview:1.2.1")

    // Enables swipe to refresh pattern
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Retrofit turns HTTP APIs into Java interfaces
    // Used to communicate with remote backends
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Modern HTTP client
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.9.3"))
    implementation("com.squareup.okhttp3:okhttp")
    testImplementation("com.squareup.okhttp3:mockwebserver")

    // SQLDelight generates typesafe kotlin APIs from SQL statements
    val sqlDelightVer = "1.5.3"
    implementation("com.squareup.sqldelight:coroutines-extensions:$sqlDelightVer")
    implementation("com.squareup.sqldelight:android-driver:$sqlDelightVer")
    testImplementation("com.squareup.sqldelight:sqlite-driver:$sqlDelightVer")

    // Dependency injection framework
    val koinAnnotationsVer = "1.0.0"
    implementation("io.insert-koin:koin-android:3.2.0")
    implementation("io.insert-koin:koin-annotations:$koinAnnotationsVer")
    ksp("io.insert-koin:koin-ksp-compiler:$koinAnnotationsVer")

    // Material design components
    implementation("com.google.android.material:material:1.6.1")

    // Used to download, cache and display images
    implementation("com.squareup.picasso:picasso:2.71828")

    // Java HTML parser
    // Used to auto-discover feed links
    implementation("org.jsoup:jsoup:1.14.3")

    // Feed parser
    // Used in standalone mode
    implementation("co.appreactor:feedk:0.2.3")

    // Core test infrastructure
    testImplementation(kotlin("test"))

    // Mocking library, better to go easy on that
    testImplementation("io.mockk:mockk:1.12.4")

    // Core test infrastructure
    androidTestImplementation(kotlin("test"))

    // An instrumentation that runs various types of test cases
    androidTestImplementation("androidx.test:runner:1.4.0")
}
