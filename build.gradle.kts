import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// --------------------------------------------------------------------------------------------------
// Android platform selection, resolved once here because both APK modules need the same answer.
//
// PULSE targets Android 17 (`compileSdk = 37`), but a build can only be compiled against a platform
// this machine actually has. When it does not, AGP tries to fetch `platforms;android-37` from the SDK
// repository — and on a runner or a sandbox where that repository is unreachable, the build dies with
// "Failed to find Platform SDK with path: platforms;android-37" before it has compiled a single
// source file. That is exactly how the CodeQL `java-kotlin` job failed: its `autobuild` runs a plain
// `./gradlew clean assemble`, so it never sees the `-Ppulse.compileSdk=<n>` that
// `.github/actions/setup-pulse-build` passes to the jobs it drives.
//
// Resolution order, shared by `:app` and `:wear`:
//   1. an explicit `-Ppulse.compileSdk` (what CI passes) is honoured as given — and the build stops
//      when that platform is not installed, because the point of asking is reproducibility;
//   2. otherwise the requested API when it is installed here;
//   3. otherwise the newest installed API at or below it — what a plain local or autobuild-style
//      invocation needs in order to get anywhere;
//   4. and when no Android SDK can be located at all, the requested API is kept, so nothing is
//      silently downgraded on a machine that has simply not installed its platform yet.
//
// A fallback is printed rather than applied quietly; the published APKs are the ones the release job
// builds with the platform the project names.
val pulseTargetSdk = 37

// Where the Android SDK lives: `local.properties` first, then the variables AGP itself reads.
val pulseSdkRoot: File? = sequenceOf(
    File(rootDir, "local.properties").takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use { stream -> load(stream) } }.getProperty("sdk.dir")
    },
    System.getenv("ANDROID_HOME"),
    System.getenv("ANDROID_SDK_ROOT"),
)
    .filterNotNull()
    .map { File(it) }
    .firstOrNull { it.isDirectory }

// The APIs installed here that can actually be compiled against: those with both a platform and a
// matching build-tools entry. An `android-36` platform without `36.0.x` build tools is not usable, and
// AGP answers its absence by reaching for the network — which is the failure being avoided here. This
// is the same on-disk test `.github/actions/setup-pulse-build/action.yml` applies, so CI and an
// unqualified local build agree on what "installed" means.
val pulseInstalledApis: Set<Int> = pulseSdkRoot?.let { root ->
    fun apis(folder: String, pattern: Regex): Set<Int> =
        File(root, folder).listFiles { file -> file.isDirectory }
            ?.mapNotNull { file -> pattern.find(file.name)?.groupValues?.get(1)?.toIntOrNull() }
            ?.toSet()
            .orEmpty()

    apis("platforms", Regex("^android-(\\d+)$")).intersect(apis("build-tools", Regex("^(\\d+)")))
} ?: emptySet()

val pulseExplicitSdk: Int? = (findProperty("pulse.compileSdk") as? String)?.let { raw ->
    raw.toIntOrNull()
        ?: throw GradleException("-Ppulse.compileSdk must be an integer API level, got \"$raw\"")
}

val pulseRequestedSdk: Int = pulseExplicitSdk ?: pulseTargetSdk

val pulseResolvedSdk: Int = when {
    pulseRequestedSdk in pulseInstalledApis -> pulseRequestedSdk
    // Asked for explicitly and not installed is an error, not a fallback: whoever passed the property
    // wants a known platform (that is how a release is reproducible), and quietly taking another one
    // is the failure mode the property exists to avoid.
    pulseExplicitSdk != null -> throw GradleException(
        "-Ppulse.compileSdk=$pulseExplicitSdk asks for platforms;android-$pulseExplicitSdk, which " +
            "is not installed" +
            (pulseSdkRoot?.let { " in ${it.path}" } ?: " (no Android SDK found here)") +
            " — install it with `sdkmanager \"platforms;android-$pulseExplicitSdk\" " +
            "\"build-tools;$pulseExplicitSdk.0.0\"`, or drop the property to compile against the " +
            "newest platform this machine has.",
    )

    else -> pulseInstalledApis.filter { it <= pulseRequestedSdk }.maxOrNull() ?: pulseRequestedSdk
}

if (pulseResolvedSdk != pulseRequestedSdk) {
    logger.lifecycle(
        "PULSE: platforms;android-$pulseRequestedSdk is not installed here — compiling against " +
            "android-$pulseResolvedSdk instead. Pass -Ppulse.compileSdk=$pulseResolvedSdk to record " +
            "that choice, or install API $pulseRequestedSdk.",
    )
}

// Read by `:app` and `:wear` as `rootProject.extra["pulseCompileSdk"]`, so the two are always
// compiled against the same platform — a phone and a watch built against different APIs is a
// data-layer mismatch waiting to happen, and there is no reason for them to ever differ.
extra["pulseCompileSdk"] = pulseResolvedSdk
