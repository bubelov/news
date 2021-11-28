import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
    id("com.squareup.sqldelight")
}

val signingPropertiesFile = rootProject.file("signing.properties")

android {
    compileSdk = 31

    defaultConfig {
        applicationId = "co.appreactor.news"
        minSdk = 26
        targetSdk = 31
        versionCode = 17
        versionName = "0.3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        setProperty("archivesBaseName", "news-$versionName")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        allWarningsAsErrors = true
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

    lint {
        disable(
            "VectorRaster",
            "VectorPath",
            "InvalidFragmentVersionForActivityResult",
            "MissingTranslation",
            "Range",
            "LintError",
        )
    }
}

sqldelight {
    database("Database") {
        sourceFolders = listOf("sqldelight")
        packageName = "db"
        deriveSchemaFromMigrations = true
    }
}

gradle.projectsEvaluated {
    tasks.withType<Test> {
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {}

            override fun beforeTest(testDescriptor: TestDescriptor) {}

            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                println("test ${testDescriptor.className} ${testDescriptor.name} ... ${result.resultType}")
            }

            override fun afterSuite(suite: TestDescriptor, result: TestResult) {}
        })
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")

    implementation("co.appreactor:feedk:0.1.4")

    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.4.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.4.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.4.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.3.5")
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.5")
    implementation("androidx.constraintlayout:constraintlayout:2.1.2")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.7.1")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp-bom:4.9.1")
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    implementation("com.github.nextcloud:Android-SingleSignOn:0.6.0")
    implementation("com.google.android.material:material:1.5.0-beta01")
    implementation("com.squareup.picasso:picasso:2.71828")
    implementation("io.insert-koin:koin-android:2.2.3")
    implementation("io.insert-koin:koin-android-ext:2.2.3")
    implementation("io.insert-koin:koin-android-viewmodel:2.2.3")
    implementation("com.squareup.sqldelight:android-driver:1.5.1")
    implementation("com.squareup.sqldelight:coroutines-extensions:1.5.1")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("org.jsoup:jsoup:1.14.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.12.0")
    testImplementation("com.squareup.sqldelight:sqlite-driver:1.5.1")

    androidTestImplementation("androidx.test:runner:1.4.0")
    androidTestImplementation("androidx.test:rules:1.4.0")
}
