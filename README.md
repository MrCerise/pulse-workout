# PULSE — Interval Coach

A complete, offline Android interval timer: precise timing that survives a locked screen, spoken
coaching from the device's own text-to-speech engine, a builder that can express everything from a
4-minute Tabata to nested circuits and ladders, and a progress history built from your real sessions.

Everything runs on the device. No account, no ads, no analytics, no backend, no network permission.

- **Package name:** `com.pulse.intervalcoach`
- **Version:** 1.0.0 (versionCode 1)
- **Minimum Android version:** 8.0 Oreo (API 26) — target/compile SDK 35
- **Languages:** English (default) and French (`values-fr`)

---

## Contents

1. [What is here](#what-is-here)
2. [Requirements](#requirements)
3. [Build, test, install](#build-test-install)
4. [Project layout](#project-layout)
5. [How the timer works](#how-the-timer-works)
6. [Feature checklist](#feature-checklist)
7. [Accessibility](#accessibility)
8. [Offline voice setup](#offline-voice-setup)
9. [Privacy](#privacy)
10. [Import, export and backups](#import-export-and-backups)
11. [Release signing](#release-signing)
12. [Known limitations](#known-limitations)
13. [Verification report](#verification-report)
14. [Troubleshooting the build](#troubleshooting-the-build)

---

## What is here

| Path | What it is |
| --- | --- |
| `engine/` | Pure Kotlin/JVM timing engine — no Android dependencies, fully unit tested |
| `app/` | The Android app (Compose, Room, DataStore, Media3, WorkManager, AppWidgets) |
| `docs/` | Contrast report and other generated documentation |
| `scripts/` | `contrast_check.py` (regenerates the contrast report), `capture-screenshots.sh` |
| `deliverables/` | The built APK and the source archive (see the paths printed at the end of a build) |

The timing engine is deliberately separate from the UI: it takes a clock as a constructor argument,
so every acceptance case (pauses, wall-clock jumps, open-ended intervals, the Tabata second) is
tested on the JVM without an emulator.

## Requirements

- **JDK 17** (Android Studio ships one: *Embedded JDK* or *jbr*)
- **Android SDK** with **platform 35** and **build-tools 35.0.0**
- **Gradle 8.9** — the wrapper is committed, so `./gradlew` fetches it
- Android Studio Ladybug or newer is optional; everything below works from the terminal

`local.properties` must point at your SDK:

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

## Build, test, install

Run from the repository root.

```bash
# 1. Unit tests for the timing engine and the app-level logic (no device needed)
./gradlew :engine:test :app:testDebugUnitTest

# 2. Static analysis
./gradlew :app:lintDebug

# 3. Debug APK (debug-signed, installable as-is)
./gradlew :app:assembleDebug
#    → app/build/outputs/apk/debug/app-debug.apk

# 4. Release APK (unsigned until you supply a keystore — see "Release signing")
./gradlew :app:assembleRelease
#    → app/build/outputs/apk/release/app-release-unsigned.apk

# 5. Install on a connected device or emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 6. Launch
adb shell am start -n com.pulse.intervalcoach.debug/com.pulse.intervalcoach.MainActivity
```

Android App Bundle, if you want it for Play:

```bash
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

Verifying the debug APK after installation:

```bash
adb shell dumpsys package com.pulse.intervalcoach.debug | grep -E "versionName|versionCode"
```

### Constrained machines

`gradle.properties` ships tuned for a 2 GB CI box (`-Xmx896m`, serial GC, `workers.max=1`,
in-process Kotlin). On a normal workstation you can raise it:

```properties
org.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g
org.gradle.workers.max=4
org.gradle.parallel=true
kotlin.compiler.execution.strategy=daemon
```

## Project layout

```
pulse/
├── engine/                                   pure-Kotlin timing engine (JVM, no Android)
│   └── src/main/kotlin/com/pulse/engine/
│       ├── Model.kt                          interval types, plan tree, validation, timeline expansion
│       └── TimerEngine.kt                    the timer state machine, clock-injected
│   └── src/test/kotlin/com/pulse/engine/TimerEngineTest.kt   29 tests
├── app/
│   └── src/main/java/com/pulse/intervalcoach/
│       ├── audio/Audio.kt                    cue sounds, haptics, speech, the anti-backlog cue gate
│       ├── data/                             Room database, DataStore preferences, repositories,
│       │                                     starter templates, text parser, backup/export
│       ├── session/                          CueScript (what gets said), SessionController,
│       │                                     WorkoutService (foreground service), music
│       ├── ui/                               Compose screens: home, workouts, builders, player,
│       │                                     summary, progress, settings, welcome, parser
│       ├── widget/                           home-screen widgets and dynamic shortcuts
│       └── work/                             WorkManager jobs (weekly backup, reminders)
│   └── src/test/java/com/pulse/intervalcoach/ app-level unit tests
├── docs/CONTRAST.md                          generated WCAG contrast report
└── scripts/contrast_check.py                 regenerates the report and fails on a regression
```

## How the timer works

- **One monotonic clock, no drift.** The engine reads `SystemClock.elapsedRealtime()` through an
  injected `MonotonicClock`. Step boundaries are computed from the anchor time of the current step,
  never by accumulating tick deltas, so a 20-minute session cannot drift and a wall-clock (NTP or
  timezone) change cannot move the timer.
- **Pause keeps the remaining time.** Pausing stores the elapsed time; resuming re-anchors the clock.
  A pause of any length therefore resumes exactly where it stopped.
- **Crossing several boundaries at once is handled.** A tick that jumps past multiple intervals
  completes each of them in order and emits one event per interval — rapid taps cannot produce
  duplicate transitions.
- **Open-ended intervals.** Manual (AMRAP, stopwatch, strength) intervals count up and wait for the
  user; totals report themselves as a minimum rather than inventing an end time.
- **Foreground service.** A workout runs in `WorkoutService` (`foregroundServiceType="mediaPlayback"`)
  with a media notification, so timing and cues continue with the screen off or while another app is
  in front. Android can still kill the process under extreme memory pressure: the session is then
  reported as **interrupted** in history rather than being marked complete.
- **Cue gate.** A Tabata block produces more announcements than a voice can finish, so the speech
  layer passes every cue through `CueGate`: interval changes always speak and flush, at most one
  follow-up cue may be queued, and countdowns are dropped when they would run past the next boundary.
- **Sound fallback.** If the device has no TTS engine (or no voice for the selected language), PULSE
  plays synthesised tones and tells you, instead of silently dropping cues.

## Feature checklist

**Destinations** — Home, Workouts, Progress, Settings (bottom navigation); planner lives inside
Progress, the template gallery inside Workouts.

- [x] Skippable three-step welcome: theme pick, optional voice sample, ready-to-start example
- [x] Home: Quick Start, favourites, recents, next planned session, weekly summary
- [x] Library: search, folders, tags, favourites, sort, list/grid, duplicate, export, delete
- [x] Template gallery: 15 original starter workouts, always computed live, all editable
- [x] Workout details: timeline, totals, notes, equipment, voice profile, preview cues
- [x] Quick builder: named workout in well under a minute
- [x] Advanced builder: interval types, per-interval overrides, repeat blocks (nesting depth 8),
      final-rest switch, L/R duplication, progressive ladders, shuffle frozen at start, saved blocks
      inserted as copies, drag reorder **and** Move up/down, duplicate, multi-select, bulk edit,
      group/ungroup, undo/redo, live total duration and colour timeline, validation, accelerated preview
- [x] Player: full-screen tabular timer, current and next interval, round and position, elapsed and
      remaining totals, progress and session timeline, explicit paused state, large controls,
      left-hand layout, instructor view for tablets
- [x] Session summary: real numbers, notes, share as image, repeat
- [x] History with per-session details and the workout as it was actually run
- [x] Progress: weekly chart, daily minutes, streak, all-time totals, local planner with reminders
- [x] Voice & audio studio: engine status, offline-voice guidance, rate, pitch, verbosity, profiles,
      cue volume, vibration, music ducking, own recordings
- [x] Settings: themes (system/light/dark/true black), dynamic colour for navigation only, high
      contrast, reduced motion, left-handed player, density, defaults, units, interruption and
      headphone behaviour, background audio, data management, help
- [x] Import / export / backup (JSON, SAF) with preview before writing and never overwriting
- [x] Empty, loading, error and permission-denied states on every data surface
- [x] Drafts preserved across process death; Save / Discard / Cancel on dirty drafts
- [x] All 16 interval types: HIIT, Tabata, Circuit, Boxing, EMOM, E2MOM, AMRAP, Strength, Run/walk,
      Stretching, Breathing, Focus/Pomodoro, Custom sequence, Compound, Stopwatch, Repeating cue
- [x] Home-screen widgets (favourites, quick start) and dynamic shortcuts
- [x] Clone with scaling, text-to-workout parser with confirmation preview, shareable summary image
- [x] Local-only storage, no analytics or ad SDKs, no secrets in the repository

**Deliberately not included:** no heart-rate, cloud sync, OAuth or wearable integration. Those
features need hardware, SDKs or credentials that do not exist in this environment; building a
simulated version would be dishonest, so the settings screen states the status instead of showing
buttons that do nothing.

## Accessibility

- TalkBack-friendly labels on controls; interval changes are announced by the app's own speech and
  are not repeated as a tick-by-tick accessibility storm.
- All touch targets are at least 48 dp; the player's main controls are considerably larger.
- Every drag interaction has a non-drag equivalent (Move up / Move down), so reordering works
  without gestures.
- Phase colours are never the only signal: each interval also shows a name and an icon.
- Timer digits use tabular numerals so the countdown does not jitter.
- Themes: light, dark, true black and system; a high-contrast switch raises text and accents.
- Reduced-motion setting removes non-essential animation.

The measured contrast ratios are in [`docs/CONTRAST.md`](docs/CONTRAST.md) and can be regenerated:

```bash
python3 scripts/contrast_check.py
```

## Offline voice setup

Announcements use the text-to-speech engine already on the device (no network calls, no cloud voices).

1. Android **Settings → System → Languages & input → Text-to-speech**
2. Choose your engine (Google Speech Services, Samsung, eSpeak, …)
3. **Install voice data** for your language, and select an offline voice
4. In PULSE: **Settings → Voice & audio studio** — the engine and whether an offline voice is
   present are shown there; use *Preview* to hear a cue

If no engine is installed, PULSE says so in Voice Studio, offers a shortcut to the system settings,
and uses sound cues during workouts.

## Privacy

- The app declares **no `INTERNET` permission** and contains no networking code; nothing it does can
  reach the network. (`ACCESS_NETWORK_STATE` appears in the merged manifest because Media3 and
  WorkManager declare it upstream — it only lets a library read whether a connection exists, and it
  is never used by PULSE's own code.)
- All data (workouts, sessions, notes, preferences, voice settings) stays in the app's private
  storage on the device.
- No analytics SDK, no advertising SDK, no crash reporter, no account, no cloud.
- Files you export go only where you send them through the system file picker.
- Android's own backup/transfer includes the database and preferences, and excludes the regenerable
  cache and recording folders (see `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`).

## Import, export and backups

- **Export:** Settings → Data → *Export all data…* writes a human-readable JSON file
  (`"format": "pulse.backup", "version": 1`) to a location you choose.
- **Import:** *Import from file…* reads the file, validates it, and shows what it contains
  (*n* workouts, *n* sessions, *n* tags) before writing anything. Imported workouts get fresh IDs and
  a “(imported)” suffix, so nothing is overwritten.
- **Automatic backups:** a weekly WorkManager job writes a dated backup into app storage and keeps
  the newest eight. Failures are surfaced in the notification channel, never silently dropped.
- **Missing media** (an image or audio file that was deleted or whose permission was revoked) is
  reported when the workout is opened; the workout itself still loads.

## Release signing

The automated release workflow signs the production APK when repository signing secrets are configured.
It fails a tag release if the signing key is missing, rather than publishing an unsigned APK as a signed
release. Ordinary pushes to `main` can still build without signing credentials.

Create a release keystore and keep it backed up somewhere private. **Never commit the keystore or its
passwords.** For example:

```bash
keytool -genkeypair -v -keystore pulse-release.jks \
  -alias pulse -keyalg RSA -keysize 4096 -validity 10000
```

In the repository's **Settings → Secrets and variables → Actions**, add these repository secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of `pulse-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `pulse` (or the alias you chose) |
| `ANDROID_KEY_PASSWORD` | Password for the key |

In PowerShell, generate the base64 value with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("pulse-release.jks"))
```

After adding the secrets, update an existing release with a signed APK by running **Actions → Build and
release → Run workflow** on `main`, with `release_tag` set to `v1.0.0`. The workflow builds and signs the
APK, then uploads it to that release. Future version tags (for example, `v1.0.1`) publish signed APKs
automatically.
## Known limitations

1. **No emulator or physical device exists in the environment this was built in.** The app was
   verified by compilation, lint and unit tests; it was never launched, so there are no screenshots
   of the running app in this repository, and no smoke test of a real workout was performed. Run
   `scripts/capture-screenshots.sh` on your own machine to produce them.
2. **Some UI copy is still English literals.** The primary screens are wired to string resources and
   translate (French is included for the whole string table), but secondary strings inside Compose
   code remain literals; `values-fr` therefore covers the string table, navigation, titles and the
   main actions first. `./gradlew :app:lintDebug` reports the strings that are not wired up yet.
3. **Hardware-dependent behaviour is untested here:** vibration, headphone-unplug detection, audio
   focus negotiation, notification actions and TTS playback all require a device to confirm.
4. **Wall-clock changes are immune by construction** (monotonic clock), but the "changed timezone
   mid-session" case has only been reasoned about, not observed on a device.
5. **Widgets and shortcuts** are implemented and declared in the manifest; their appearance on a
   launcher is unverified without a device.
6. **Tablet instructor view** is a Compose layout adaptation; it has not been seen on a large screen.
7. **Lint** runs clean of errors and fatal issues; it reports warnings (unused resources from the
   string table, newer dependency versions available, plurals suggestions). Those are visible in
   `app/build/reports/lint-results-debug.html` and are not suppressed.

## Verification report

Everything below is the output of commands actually run in the build environment; nothing here is
estimated.

| Check | Command | Result |
| --- | --- | --- |
| Engine unit tests | `./gradlew :engine:test` | 29 tests, 0 failures |
| App unit tests | `./gradlew :app:testDebugUnitTest` | 29 tests, 0 failures (parser, backups, cue gate, migrations) |
| Kotlin compilation | `./gradlew :app:compileDebugKotlin` | 0 errors |
| Debug APK | `./gradlew :app:assembleDebug` | `app-debug.apk` produced |
| Static analysis | `./gradlew :app:lintDebug` | see `app/build/reports/lint-results-debug.html` |
| Contrast | `python3 scripts/contrast_check.py` | all pairs pass (see `docs/CONTRAST.md`) |
| Install / smoke test | `adb install …` | **not run** — no emulator or device in this environment |
| Screenshots | — | **not produced** — no running app to capture |

Test coverage includes the acceptance cases: Tabata 4:00 with the final rest and 3:50 without,
pause/resume preserving remaining time, wall-clock immunity, no duplicate transitions from rapid
controls, nested groups and ladders, open-ended intervals, the expansion limits (10,000 intervals /
24 hours / depth 8), JSON round-trips, and a real SQLite migration replayed against the shipped
migration SQL.

## Troubleshooting the build

- **`SDK location not found`** — create `local.properties` with `sdk.dir=…`.
- **`Unsupported class file major version` / JDK errors** — use JDK 17:
  `JAVA_HOME=/path/to/jdk-17 ./gradlew …`
- **The build is killed on a small machine** — keep the values in `gradle.properties` low and run
  with `--max-workers=1`; the pre-tuned defaults are the ones the tests above were produced with.
- **`app-release-unsigned.apk` cannot be installed** — that is expected without a keystore; install
  the debug APK instead.
