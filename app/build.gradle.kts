import java.io.File
import java.time.Duration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}
val releaseStoreFile = System.getenv("PULSE_RELEASE_STORE_FILE")
val releaseStorePassword = System.getenv("PULSE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("PULSE_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("PULSE_RELEASE_KEY_PASSWORD")
val releaseSigningConfigured = !releaseStoreFile.isNullOrBlank() &&
    File(releaseStoreFile).isFile &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

// Android 17 (API 37) is the compile target, but *which* platform is used is decided once, in the
// root build script, from what this machine has installed: a build cannot compile against a platform
// it cannot find, and `./gradlew assemble` on a machine without API 37 dies on the SDK download
// ("Failed to find Platform SDK with path: platforms;android-37") before compiling a line — which is
// how the CodeQL autobuild failed, since it does not pass the `-Ppulse.compileSdk` our CI action sets.
// The override is still honoured exactly, so release artifacts are built against the documented API.
// See `build.gradle.kts` for the resolution order.
val pulseCompileSdk: Int = rootProject.extra.takeIf { it.has("pulseCompileSdk") }
    ?.get("pulseCompileSdk") as? Int ?: 37

android {
    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    namespace = "com.pulse.intervalcoach"
    compileSdk = pulseCompileSdk

    defaultConfig {
        applicationId = "com.pulse.intervalcoach"
        minSdk = 26
        targetSdk = pulseCompileSdk
        versionCode = 22
        versionName = "1.7.6"
        // Package all locales delivered by Crowdin; missing strings fall back to English.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            signingConfig = if (releaseSigningConfigured) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Local builds without signing credentials continue to produce an unsigned APK.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Unit tests must never hang a CI box: bound the worker JVM and log progress.
tasks.withType<Test>().configureEach {
    timeout.set(Duration.ofMinutes(8))
    maxHeapSize = "384m"
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":sync"))
implementation(project(":library"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime.ktx)

    // Health & fitness integrations (Health Connect + Google Fit)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.gms.play.services.fitness)
    implementation(libs.gms.play.services.auth)

    // Wear OS companion link (workout library/preferences sync, session mirroring and control)
    implementation(libs.gms.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.sqlite.jdbc)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
