<div align="center">

<img src="docs/brand/pulse.svg" width="88" height="88" alt="INTERVALS" />

# INTERVALS

**Offline-first interval timer and workout coach for Android.**

Precise timing that survives a locked screen, spoken coaching from your device's own
text-to-speech engine, a builder that expresses everything from a 4-minute Tabata to nested
circuits and ladders, history built from your real sessions — and workout export to
Health Connect.

[![CI](https://github.com/MrCerise/pulse-workout/actions/workflows/build-release.yml/badge.svg?branch=main)](https://github.com/MrCerise/pulse-workout/actions/workflows/build-release.yml)
[![Release](https://img.shields.io/github/v/release/MrCerise/pulse-workout?sort=semver&style=flat-square)](https://github.com/MrCerise/pulse-workout/releases/latest)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?style=flat-square&logo=android)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Crowdin](https://badges.crowdin.net/pulse-workout/localized.svg)](https://crowdin.com/project/pulse-workout)

<a href="https://crowdin.com/?utm_term=click-badge-add-on"><img style="width:140;height:40px" src="https://badges.crowdin.net/badge/light/crowdin-on-dark.png" srcset="https://badges.crowdin.net/badge/light/crowdin-on-dark.png 1x,https://badges.crowdin.net/badge/light/crowdin-on-dark@2x.png 2x" alt="Crowdin | Agile localization for tech companies" /></a>

</div>

---

INTERVALS runs entirely on your device. **No account, no ads, no analytics, no backend.** The only data
that ever leaves the app is what you explicitly share — a health record written to Health Connect,
a backup file you exported, or a prompt you pasted into an AI tool of your own choosing.

**Contents** — [Highlights](#highlights) · [Features](#features) · [Quick start](#quick-start) ·
[Documentation](#documentation) · [Repository layout](#repository-layout) ·
[Accessibility](#accessibility) · [Offline voice](#offline-voice-setup) ·
[Contributing](#contributing) · [Security](#security) · [License](#license) ·
[Troubleshooting](#troubleshooting)

**Releases** — [latest APKs](https://github.com/MrCerise/pulse-workout/releases/latest) ·
[CHANGELOG.md](CHANGELOG.md) · store releases run through the **release train**
(`.github/workflows/release-train.yml`): a tagged `v*` push builds signed phone/watch APKs and
AABs once, publishes them to GitHub Releases, and uploads the phone AAB to Play closed testing
as a draft. Manual trains support internal, closed, open and production tracks. See
[release setup and inputs](docs/RELEASING.md#manual-dispatch).

## Highlights

- **Timing that does not drift.** The engine is driven by one monotonic clock and computes step
  boundaries from an anchor time, never from accumulated ticks. Wall-clock changes, timezones and
  NTP corrections cannot move the timer, and pausing preserves the remaining time exactly.
- **Runs with the screen off.** A foreground service with a media notification keeps timing and
  cues alive while the phone is locked or another app is in front.
- **A coach that keeps up, not one that lags behind.** Every cue passes through a gate so a Tabata
  block cannot build a speech backlog: interval changes always speak, countdowns are dropped when
  they would arrive late, and if the device has no TTS voice INTERVALS falls back to tones and says so.
- **A builder that scales with your workout.** Sixteen interval types, repeat blocks nested eight
  deep, ladders, supersets, per-interval overrides, drag reorder *and* Move up/down, undo/redo and a
  live timeline — in one screen.
- **Bring your own AI.** Copy a schema-aware prompt, paste the response back, preview the expanded
  timeline and save. No API keys, no network calls, nothing sent on your behalf.
- **Health Connect.** Finished sessions written as workout
  sessions with per-interval segments.
- **On your wrist, on its own or paired.** The Wear OS app runs the same engine on the watch —
  same drift-free timing, haptic cues, the same theme. With no phone it is a complete app: the
  bundled catalogue seeds itself, sessions are recorded on the wrist, and History says what ran.
  Pair it and your library, settings and live sessions mirror both ways, either screen can control
  the workout, and watch sessions land in the phone's history.

## Features

| Area | What you get |
| --- | --- |
| **Timing** | Monotonic-clock engine, absolute anchors, open-ended (AMRAP/stopwatch/strength) intervals, accelerated preview, per-session timeline |
| **Cues** | On-device TTS with speech-text overrides, tone fallback, haptics, a cue gate, audio ducking with a live depth control, background music |
| **Building** | Quick builder, advanced builder (nesting depth 8, ladders, group/ungroup, multi-select, bulk edit), 15 starter templates, clone with scaling |
| **Library** | Search, tags, favourites, folders, sort, list/grid, duplicate, share, delete, JSON import/export |
| **Player** | Full-screen tabular timer, current/next interval, round and position, elapsed and remaining totals, left-handed layout, tablet instructor view |
| **History & progress** | Per-session details with the workout as it was run, weekly chart, daily minutes, streaks, all-time totals, a monthly goal and a local planner with reminders; starter templates live right on Today |
| **Today** | One-tap category starts, quick work/rest/rounds builder, favourites, templates, weekly progress and goals — everything one tap from the dashboard |
| **Health** | Health Connect: session writes, 7-day backfill, an honest setup screen |
| **Voice studio** | Engine and offline-voice status, rate, pitch, verbosity, profiles, cue volume, vibration, ducking switch and depth, your own recordings |
| **Look & feel** | Light, dark, true black and system themes, high contrast, reduced motion, dynamic colour, adjustable density; 125 contrast pairs measured in CI |
| **System** | Home-screen widgets, dynamic shortcuts, drafts that survive process death |
| **Wear OS** | Same engine and theme as the phone, usable with no phone at all (bundled workouts, on-watch history, a link switch); when paired, library/preferences sync, two-way live session mirroring and remote control, and watch sessions saved to the phone's history (and Health Connect) |
| **Locales** | English and French complete; every Crowdin-approved locale is packaged and falls back to English string by string; Settings → Appearance follows the device or pins English, French, German, Spanish or Italian |

**Deliberately not included:** no Bluetooth chest-strap pairing, no cloud sync of your own, no
account system, no analytics or crash-reporting SDK. Health data flows only through the platform
connectors you authorise.

## Quick start

You need **JDK 17** and the **Android SDK** with **platform 37** and **build-tools 37.0.0**
(36.0.0 also works). Gradle 8.9 comes from the committed wrapper. Android Studio is optional.

```bash
git clone https://github.com/MrCerise/pulse-workout.git
cd pulse-workout

# Point the build at your SDK (this file is gitignored)
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Unit tests — engine, sync protocol, shared catalogue and both apps, no device needed
./gradlew :engine:test :sync:test :library:test :app:testDebugUnitTest :wear:testDebugUnitTest

# Static analysis
./gradlew :app:lintDebug

# Debug APK, then install it
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build uses the package id `com.mrcerise.intervalcoach.debug`, so it installs alongside a
release build without touching your data.

Release artefacts: `./gradlew :app:assembleRelease` (unsigned without a keystore — see
[docs/RELEASING.md](docs/RELEASING.md)) and `./gradlew :app:bundleRelease` for an AAB.

> **On a machine with 4 GB of RAM or less**, lower the heap and turn parallelism off in
> `gradle.properties`: `org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC`
> and `org.gradle.parallel=false`.

Build problems? See [Troubleshooting](#troubleshooting) at the bottom of this page.

## Documentation

| Document | What is in it |
| --- | --- |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Module map, the timing engine, and how the app layers fit together |
| [docs/DESIGN.md](docs/DESIGN.md) | The "Signal" design system: colour, type, spacing, components |
| [docs/CONTRAST.md](docs/CONTRAST.md) | Generated WCAG 2.1 contrast report (125 pairs, three themes) |
| [docs/ACCEPTANCE.md](docs/ACCEPTANCE.md) | The acceptance cases and the evidence behind each one |
| [docs/VERIFICATION.md](docs/VERIFICATION.md) | What is verified, how, and what is not verified yet |
| [docs/DATA.md](docs/DATA.md) | Storage, privacy guarantees, export/import and backups |
| [docs/WORKOUT_JSON.md](docs/WORKOUT_JSON.md) | The `pulse.workouts` JSON exchange format and the AI workflow |
| [docs/TRANSLATING.md](docs/TRANSLATING.md) | Translating INTERVALS on Crowdin, and maintainer setup |
| [docs/RELEASING.md](docs/RELEASING.md) | Versioning, signing and how a release is cut |
| [docs/RELEASE_1.7.0.md](docs/RELEASE_1.7.0.md) and friends | Per-release verification notes |

## Repository layout

```
pulse-workout/
├── engine/          pure-Kotlin timing engine (JVM, no Android deps) + 29 unit tests
├── sync/            pure-Kotlin phone↔watch protocol (payloads, codecs) + round-trip tests
├── library/         the bundled workout catalogue both APKs seed from + catalogue tests
├── app/             the Android app — Compose, Room, DataStore, Media3, WorkManager, widgets
│   └── src/main/java/com/mrcerise/intervalcoach/
│       ├── audio/     cue sounds, haptics, speech, the cue gate
│       ├── data/      Room, DataStore, repositories, JSON import, backups, category registry
│       ├── health/    Health Connect
│       ├── locale/    the in-app language override and what it applies to
│       ├── session/   CueScript, SessionController, WorkoutService, music
│       ├── ui/        theme tokens, shared components, screens
│       ├── wear/      the phone half of the Wear OS sync link
│       ├── widget/    home-screen widgets and dynamic shortcuts
│       └── work/      WorkManager jobs (weekly backup, reminders)
├── wear/            the Wear OS app — same engine, mirrored theme, the watch half of the link,
│                    its own history and a standalone mode that needs no phone
├── docs/            documentation, the design system and generated reports
├── scripts/         resource, brand, contrast and Kotlin cross-reference gates
└── .github/         CI workflow, issue and PR templates
```

The timing engine is deliberately separate from the UI: it takes a clock as a constructor argument,
so every acceptance case — pauses, wall-clock jumps, open-ended intervals, the Tabata second — is
tested on the JVM without an emulator.

## Accessibility

- TalkBack-friendly labels on controls; interval changes are announced by the app's own speech and
  are not repeated as a tick-by-tick accessibility storm.
- Touch targets are at least 48 dp, every drag interaction has a non-drag equivalent, and colour is
  never the only signal — each interval also shows a name, a dot and its position in the timeline.
- Text clears 4.5:1 in every theme and control boundaries clear 3:1 (WCAG 1.4.11). The measured
  numbers are in [docs/CONTRAST.md](docs/CONTRAST.md), and the rules behind them in
  [docs/DESIGN.md](docs/DESIGN.md).
- Timer digits use tabular figures so the countdown does not jitter. High-contrast and reduced-motion
  switches are honoured everywhere.

## Offline voice setup

Announcements use the text-to-speech engine already on the device — no network calls, no cloud
voices.

1. Android **Settings → System → Languages & input → Text-to-speech**
2. Choose your engine (Google Speech Services, Samsung, eSpeak, …)
3. **Install voice data** for your language and select an offline voice
4. In INTERVALS: **Settings → Voice & audio studio** shows the engine and whether an offline voice is
   present; use *Preview* to hear a cue

If no engine is installed, INTERVALS says so in Voice Studio, offers a shortcut to the system settings,
and uses sound cues during workouts.

## Contributing

Bug reports, translations, documentation fixes, tests and code are all welcome. Start with
[CONTRIBUTING.md](CONTRIBUTING.md); it covers the setup, the checks CI runs, and the house rules
(no tracking SDKs, no new dependencies without a discussion, translations on Crowdin rather than in
PRs). Good first issues are labelled [`good first issue`][gfi].

Translators: join the [Crowdin project](https://crowdin.com/project/pulse-workout) — no Android
development environment required.

## Security

INTERVALS stores workout history and health data, so please report vulnerabilities privately rather
than in a public issue. [SECURITY.md](SECURITY.md) explains how to reach us, what is in scope and
what to expect.

## License

INTERVALS is released under the [MIT License](LICENSE).

Typographic work uses [Inter](https://rsms.me/inter/) (SIL Open Font License 1.1, shipped in
`app/src/main/res/raw/inter_ofl.txt`). Health data is read and written through
[Health Connect](https://developer.android.com/health-and-fitness/guides/health-connect), and
translations are managed by the community on [Crowdin](https://crowdin.com/project/pulse-workout).

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `SDK location not found` | Create `local.properties` with `sdk.dir=/absolute/path/to/Android/sdk` |
| `Unsupported class file major version` | Use JDK 17: `JAVA_HOME=/path/to/jdk-17 ./gradlew …` |
| `Failed to find Platform SDK with path: platforms;android-37` | The Android 17 platform is not installed here and the SDK repository cannot be reached. The build compiles against the newest platform you *do* have (install it with `sdkmanager "platforms;android-36" "build-tools;36.0.0"`); pass `-Ppulse.compileSdk=37` to insist on the documented one, which then requires the download to have succeeded |
| The build is killed on a small machine | Lower `org.gradle.jvmargs`, set `org.gradle.parallel=false`, or run with `--max-workers=1` |
| `app-release-unsigned.apk` will not install | Expected without a keystore — install the debug APK, or see [docs/RELEASING.md](docs/RELEASING.md) |

[gfi]: https://github.com/MrCerise/pulse-workout/labels/good%20first%20issue
