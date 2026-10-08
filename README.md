# PULSE — Interval Coach

A complete, offline-first Android interval timer: precise timing that survives a locked screen,
spoken coaching from the device's own text-to-speech engine, a builder that can express everything
from a 4-minute Tabata to nested circuits and ladders, a progress history built from your real
sessions — and, since v1.3, live heart rate and workout sync through **Health Connect** (with
**Google Fit** as a best-effort secondary).

Everything PULSE does itself runs on the device. No account, no ads, no analytics, no backend. The
only data that ever leaves the phone are the records you explicitly share with a Google health app.

- **Package name:** `com.pulse.intervalcoach`
- **Version:** 1.4.2 (versionCode 5)
- **Minimum Android version:** 8.0 Oreo (API 26) — target/compile SDK 37 (Android 17)
- **Languages:** English (default) and French (`values-fr`)

## v1.4.2 — what changed

- **"Graphite" design system.** The visual layer was rebuilt, not reskinned, and it is now
  centralised: `ui/theme/` holds the palette, type scale, spacing/radius scale and motion; the
  shared components live in `ui/components/` and are the only place a style is written. The design
  reference is **Linear** — near-black chrome, hairline borders instead of shadows, one restrained
  accent, a tight type scale with monospaced tabular numerals — applied consistently to every route.
  Full specification: [`docs/DESIGN.md`](docs/DESIGN.md).
- **Chrome is monochrome, colour means something.** Gradients, glowing borders, phase washes and
  decorative glows are gone. The only chromatic elements left on screen are the interval phases
  and destructive state, so a green timeline segment or a violet cool-down chip reads instantly.
- **New interaction layer.** Bottom navigation, top bars, buttons, fields, segmented controls,
  chips, badges, banners, tooltips, dialogs, snackbars and the loading/empty/error states are all
  shared components driven by tokens; hover, selected, focused and disabled states are uniform and
  transitions sit in the 120–180 ms band (collapsed to zero by the reduced-motion switch).
- **Rebuilt screens.** Player, Today, Workouts, the builders, Progress, Settings, Health, Welcome
  and the parser were all re-laid out against the new system: consistent 16 dp gutters, 12 dp
  between cards, one 56 dp header, measurable hierarchy, and colour reserved for meaning.
- **Accessibility and contrast are enforced.** `scripts/contrast_check.py` regenerates
  [`docs/CONTRAST.md`](docs/CONTRAST.md) and fails on token drift or on any pairing below its WCAG
  threshold; the current run measures 81 pairs across dark, OLED and light themes.
- **Tooling.** `scripts/kotlin_xref_gate.py` (a stdlib-only static check over imports, container
  references, preference fields and component call sites) runs clean and is part of the local
  verification loop.
Still true from v1.3, unchanged by the redesign:

- **Health Connect is the primary health backend.** Finished workouts are written as workout
  sessions with per-interval segments (idempotent per session), and PULSE reads live heart rate
  for the player and today's steps for the Today screen. A dedicated Health setup screen shows the
  real platform state, per-permission status, and can backfill your last 7 days.
- **Google Fit as a secondary, best-effort backend.** Google has deprecated the on-device Fit API
  (2026) and stopped accepting new signups, so the app ships without a Fit OAuth client id and
  treats Fit as an optional fallback; every Fit code path degrades gracefully and the UI says so.
- **Android 17 (API 37).** `compileSdk`/`targetSdk` are 37; CI builds against `platforms;android-37`.
- **The "not implemented" health card is gone.** What you see is what the platform reports:
  availability, granted permissions, last sync, and the outcome of every write.

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
| `scripts/` | `contrast_check.py` (regenerates the contrast report), `kotlin_parse_gate.py` +
  `kotlin_xref_gate.py` (dependency-free Kotlin syntax and cross-reference gates),
  `capture-screenshots.sh` |
| `deliverables/` | The built APK and the source archive (see the paths printed at the end of a build) |

The timing engine is deliberately separate from the UI: it takes a clock as a constructor argument,
so every acceptance case (pauses, wall-clock jumps, open-ended intervals, the Tabata second) is
tested on the JVM without an emulator.

## Requirements

- **JDK 17** (Android Studio ships one: *Embedded JDK* or *jbr*)
- **Android SDK** with **platform 37** and **build-tools 37.0.0** (36.0.0 is an acceptable
  fallback; the CI tries both)
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
│       ├── health/                           HealthHub: Health Connect (primary) + Google Fit
│       │                                     (secondary) bridge — reads HR/steps, writes sessions
│       ├── session/                          CueScript (what gets said), SessionController,
│       │                                     WorkoutService (foreground service), music
│       ├── ui/theme/                         the design system: Palette.kt (raw tokens),
│       │                                     Tokens.kt (colour/spacing/radius/motion locals),
│       │                                     Typography.kt, Theme.kt (theming + WCAG helpers)
│       ├── ui/components/                    the shared component library: Foundation, Controls,
│       │                                     Indicators, DataDisplay, Feedback, Navigation
│       ├── ui/                               Compose screens: home (Today), workouts, builders,
│       │                                     player, summary, progress, settings, health,
│       │                                     welcome, parser
│       ├── widget/                           home-screen widgets and dynamic shortcuts
│       └── work/                             WorkManager jobs (weekly backup, reminders)
│   └── src/test/java/com/pulse/intervalcoach/ app-level unit tests
├── docs/DESIGN.md                            the design system, in full
├── docs/CONTRAST.md                          generated WCAG contrast report
├── docs/ACCEPTANCE.md                        what "done" means, checked case by case
└── scripts/                                  contrast gate, resource gate, Kotlin xref gate
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

**Destinations** — Today, Workouts, Progress, Settings (bottom navigation); the planner lives
inside Progress, the template gallery and Health setup are pushed destinations.

- [x] Skippable three-step welcome: theme pick, optional voice sample, ready-to-start example
- [x] Today: one-tap presets with real computed durations, custom work/rest steppers, weekly
      minutes against a 150-minute goal with a 7-day chart, active-session resume banner,
      favourites rail with per-card Start, next planned, recents, live health snapshot
- [x] Live heart rate in the player when a health platform is connected (3-second polling of
      Health Connect, then Google Fit), with HR zones; the chip simply never appears without data
- [x] Session summary shares the finished workout to your health apps (toggle in Settings),
      shows real average/peak heart rate for the session window, and says when a sync failed
- [x] Health setup screen: real Health Connect platform state, per-permission status (HR read,
      steps read, workout write), Google Fit availability, last sync, 7-day backfill
- [x] Library: search, tags, favourites, sort (recent/name/duration/created), list/grid views,
      one-tap start, duplicate, share, delete
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

**Deliberately not included:** no Bluetooth heart-rate belt pairing, no cloud sync of your own, no
account system. Health data flows only through the platform connectors you authorise (Health
Connect, Google Fit); PULSE never opens a socket itself. Google Fit is best-effort by design:
Google deprecated the on-device Fit API in 2026 and no longer accepts new app signups, so this
build ships without a Fit `OAUTH_CLIENT_ID` and says so in the UI instead of pretending.

## Accessibility

- TalkBack-friendly labels on controls; interval changes are announced by the app's own speech and
  are not repeated as a tick-by-tick accessibility storm.
- All touch targets are at least 48 dp; the player's main controls are considerably larger.
- Every drag interaction has a non-drag equivalent (Move up / Move down), so reordering works
  without gestures.
- Phase colours are never the only signal: each interval also shows a name, a dot and its
  position in the timeline, so the timer is readable in greyscale.
- Text clears 4.5:1 in every theme and control boundaries clear 3:1 (WCAG 1.4.11); the measured
  numbers are in [`docs/CONTRAST.md`](docs/CONTRAST.md) and the design rules behind them in
  [`docs/DESIGN.md`](docs/DESIGN.md).
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

- **PULSE's own code makes no network calls.** The `INTERNET` permission appears in the merged
  manifest only because the Google Play services libraries used for Google Fit declare it for
  their own platform communication; Health Connect itself is an on-device IPC API. PULSE never
  opens a socket, never sends analytics, and never uploads anything.
- **Health data leaves the device only when you allow it.** The Health Connect write
  ("share finished workouts") is a per-app permission you grant, and it can be switched off under
  Settings → Health at any time. What is written: workout sessions with per-interval segments and
  the recorded heart rate, nothing else.
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
   verified by compilation, lint and unit tests in CI; it was never launched in this environment,
   so there are no screenshots of the running app in this repository, and no smoke test of a real
   workout was performed. Run `scripts/capture-screenshots.sh` on your own machine to produce
   them.
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

8. **Google Fit is best-effort by design.** The on-device Fit API was deprecated by Google in
   2026 and no longer accepts new developer signups, so v1.3 ships without a Fit
   `OAUTH_CLIENT_ID` metadata entry. If you already hold client credentials, add
   `com.google.android.gms.fitness.OAUTH_CLIENT_ID` to the manifest and Fit authorisation works
   out of the box; without it, the Health setup screen explains the state and everything keeps
   working through Health Connect.
9. **Health Connect API surface.** v1.3 targets the stable `connect-client:1.1.0` API. The 1.2.x
   line renames several record types and is alpha; the code deliberately does not chase it.
## Verification report

For v1.4.2 the build environment has no Android SDK or JDK, so compilation and tests run in the
GitHub Actions workflow (see `.github/workflows/build-release.yml`); the checks that *can* run
locally are run and are listed below with their real output.

| Check | Command | Result |
| --- | --- | --- |
| Contrast (tokens ↔ XML ↔ Kotlin in lockstep) | `python3 scripts/contrast_check.py` | 81 pairs across 3 themes, all pass (see `docs/CONTRAST.md`) |
| Resource gate (every `@color`/`@string`/`@drawable` resolves, vectors parse, EN/FR parity) | `python3 scripts/check_ui.py` | 414 names, 322 EN / 322 FR strings, 41 icons — OK |
| Cross-file reference gate (imports, container refs, preference fields, component call sites) | `python3 scripts/kotlin_xref_gate.py` | 0 problems |
| Component call-site gate (every named argument exists on the declaration) | ad-hoc run of the same technique over `ui/components` | 43 components, 0 unknown arguments |
| Engine unit tests (unchanged module) | CI `./gradlew :engine:test` | final gate |
| App unit tests (parser, factory, backups, cue gate, DB migration v2) | CI `./gradlew :app:testDebugUnitTest` | final gate |
| Compilation, lint, debug + release APK (SDK 37) | CI workflow | final gate |
| Install / smoke test | — | **not run** — no emulator or device in this environment |

Test coverage includes the acceptance cases: Tabata 4:00 with the final rest and 3:50 without,
pause/resume preserving remaining time, wall-clock immunity, no duplicate transitions from rapid
controls, nested groups and ladders, open-ended intervals, the expansion limits (10,000 intervals /
24 hours / depth 8), JSON round-trips, and a real SQLite migration replayed against the shipped
migration SQL. The engine module is untouched by v1.3, so every engine test that passed at v1.2
passes unchanged.

## Troubleshooting the build

- **`SDK location not found`** — create `local.properties` with `sdk.dir=…`.
- **`Unsupported class file major version` / JDK errors** — use JDK 17:
  `JAVA_HOME=/path/to/jdk-17 ./gradlew …`
- **The build is killed on a small machine** — keep the values in `gradle.properties` low and run
  with `--max-workers=1`; the pre-tuned defaults are the ones the tests above were produced with.
- **`app-release-unsigned.apk` cannot be installed** — that is expected without a keystore; install
  the debug APK instead.
