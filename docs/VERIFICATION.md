# Verification

What has actually been checked, how, and — just as important — what has not. This page is kept
honest: "verified" means an automated check ran and passed, "implemented" means the code path exists
and compiles but only a device can exercise it, and "not run" means no device or emulator was
available.

- [Where the checks run](#where-the-checks-run)
- [Local checks](#local-checks)
- [CI checks](#ci-checks)
- [What is not verified](#what-is-not-verified)
- [Known limitations](#known-limitations)

## Where the checks run

| Environment | What it can prove |
| --- | --- |
| Any machine with Python 3 | Resources, vectors, EN/FR parity, brand assets, contrast, Kotlin syntax and cross-reference gates |
| Any machine with JDK 17 | `:engine:test` — the whole timing engine, no Android SDK needed |
| CI (GitHub Actions) | `:engine:test`, `:app:testDebugUnitTest`, lint, and signed debug/release APK builds against SDK 37 |
| A device or emulator | Everything platform-dependent: notifications, audio focus, TTS, vibration, widgets, TalkBack, Health Connect |

The development environment this project has been built in has **no Android SDK, no JDK and no
emulator**, so the CI workflow is the authoritative build gate and the local gates below are the ones
that can run anywhere.

## Local checks

| Check | Command | Result |
| --- | --- | --- |
| Contrast (tokens ↔ XML ↔ Kotlin in lockstep) | `python3 scripts/contrast_check.py` | 125 pairs across 3 themes, all pass — see [CONTRAST.md](CONTRAST.md) |
| Kotlin syntax gate (parse of every source file) | `python3 scripts/kotlin_parse_gate.py` | 48 files, 0 syntax errors |
| Resource gate (`@color`/`@string`/`@drawable` resolve, vectors parse, EN/FR parity) | `python3 scripts/check_ui.py` | 432 names, 340 EN / 340 FR strings, 42 icons — OK |
| Brand gate | `python3 scripts/check_brand.py` | OK |
| Cross-file reference gate (imports, container refs, preference fields, component call sites) | `python3 scripts/kotlin_xref_gate.py` | 0 problems |
| Component call-site gate (every named argument exists on the declaration) | ad-hoc run of the same technique over `ui/components` | 43 components, 0 unknown arguments |

`contrast_check.py` rewrites [CONTRAST.md](CONTRAST.md) every run; CI fails if the committed report
does not match, so the file is generated, never hand-edited.

## CI checks

| Check | Job | Result |
| --- | --- | --- |
| Engine unit tests | `unit-tests` | final gate |
| App unit tests (AI import, themes, factory, backups, cues, ducking, DB migration v2, builder route) | `unit-tests` | final gate |
| Resource, brand, contrast and xref gates | `resource-checks` | final gate |
| Compilation, lint, debug + release APK (SDK 37) | `release` (on tags) | final gate |
| Install / smoke test | — | **not run** — no emulator or device |

Unit-test coverage includes the acceptance cases: Tabata 4:00 with the final rest and 3:50 without,
pause/resume preserving remaining time, wall-clock immunity, no duplicate transitions from rapid
controls, nested groups and ladders, open-ended intervals, the expansion limits (10,000 intervals /
24 hours / depth 8), JSON round-trips, and a real SQLite migration replayed against the shipped
migration SQL. The case-by-case mapping is in [ACCEPTANCE.md](ACCEPTANCE.md).

## What is not verified

Anything that needs hardware or a running Android system has **not** been observed:

- installing and launching the APK, and running a full workout end to end;
- vibration, headphone-unplug detection, audio-focus negotiation, notification actions, TTS output;
- widget and shortcut appearance on a launcher;
- the tablet instructor view on a large screen;
- TalkBack navigation and the "changed timezone mid-session" case, which are reasoned about rather
  than observed.

## Known limitations

1. **No emulator or physical device exists in the environment this project is developed in.** The app
   is verified by compilation, lint and unit tests in CI; it has never been launched there, so there
   are no screenshots of the running app in the repository. Run `scripts/capture-screenshots.sh` on
   your own machine to produce them.
2. **Some UI copy is still English literals.** Primary screens are wired to string resources and
   translate (French covers the whole string table), but secondary strings inside Compose code remain
   literals. `./gradlew :app:lintDebug` reports the strings that are not wired up yet.
3. **Hardware-dependent behaviour is untested here:** vibration, headphone-unplug detection, audio
   focus negotiation, notification actions and TTS playback all require a device to confirm.
4. **Wall-clock changes are immune by construction** (monotonic clock), but the "changed timezone
   mid-session" case has only been reasoned about, not observed on a device.
5. **Widgets and shortcuts** are implemented and declared in the manifest; their appearance on a
   launcher is unverified without a device.
6. **Tablet instructor view** is a Compose layout adaptation; it has not been seen on a large screen.
7. **Lint** runs clean of errors and fatal issues, but reports warnings (unused resources from the
   string table, newer dependency versions available, plurals suggestions). They are visible in
   `app/build/reports/lint-results-debug.html` and are deliberately not suppressed.
8. **Google Fit is best-effort by design.** The on-device Fit API was deprecated by Google and no
   longer accepts new developer signups, so PULSE ships without a Fit `OAUTH_CLIENT_ID` metadata
   entry. If you already hold client credentials, add
   `com.google.android.gms.fitness.OAUTH_CLIENT_ID` to the manifest and Fit authorisation works out
   of the box; without it, the Health setup screen explains the state and everything keeps working
   through Health Connect.
9. **Health Connect API surface.** PULSE targets the stable `connect-client:1.1.0` API. The 1.2.x
   line renames several record types and is alpha; the code deliberately does not chase it.
