import java.io.File
import java.time.Duration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Same keystore as the phone app: both APKs are published together from one release.
val releaseStoreFile = System.getenv("PULSE_RELEASE_STORE_FILE")
val releaseStorePassword = System.getenv("PULSE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("PULSE_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("PULSE_RELEASE_KEY_PASSWORD")
val releaseSigningConfigured = !releaseStoreFile.isNullOrBlank() &&
    File(releaseStoreFile).isFile &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

// Same platform the phone app is built against, resolved once in the root build script from what is
// installed here — the two APKs share a data layer, so they must never disagree on the API they
// compile against. Pass -Ppulse.compileSdk=<n> to pin it (that is what the CI action does).
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

    namespace = "com.pulse.intervalcoach.wear"

    // Same application id as the phone app: the Wear data layer links apps by package name, so
    // this is what makes the two halves of PULSE find each other.
    defaultConfig {
        applicationId = "com.pulse.intervalcoach"
        minSdk = 26
        targetSdk = pulseCompileSdk
        versionCode = 22
        versionName = "1.7.6"
        vectorDrawables.useSupportLibrary = true
    }

    compileSdk = pulseCompileSdk

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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

dependencies {
    implementation(project(":engine"))
    implementation(project(":sync"))
    implementation(project(":library"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.gms.play.services.wearable)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
