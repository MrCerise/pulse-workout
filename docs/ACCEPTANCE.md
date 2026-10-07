# Acceptance cases

The seventeen acceptance cases from the project brief, each mapped to the evidence that actually
exists. Statuses are honest: **verified** means an automated test ran in this environment and passed,
**implemented** means the code path exists but only a device can exercise it, and **not possible
here** means the environment lacks what the check requires.

| # | Case | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Eight rounds of 20 s / 10 s last 4:00 with the final rest and 3:50 without | **verified** | `TimerEngineTest` (16 steps / 240 s, 15 steps / 230 s) and `ParserAndFactoryTest.the quick start tabata preset is the exact four minute protocol`; the Tabata template and the Quick Start preset both use the textbook protocol |
| 2 | 30 s interval paused after 12 s, resumed after a 20 s pause, still has 18 s left | **verified** | `TimerEngineTest` — the elapsed value is frozen on pause and re-anchored on resume |
| 3 | Changing the wall clock does not change an active interval | **verified by construction** | The engine only ever reads the injected `MonotonicClock`; `SystemClock.elapsedRealtime()` is supplied by the app. `TimerEngineTest` drives a clock that jumps around while an interval runs |
| 4 | Nested workouts produce the right order, count and total | **verified** | `TimerEngineTest` — preparation + 3 × (interval + 2 × superset + rest) + cool-down = 20 steps, 430 s, with `groupBreadcrumb` and round copy asserted |
| 5 | Next / Previous / Add time / Pause / notification controls cannot duplicate transitions or history | **verified in the engine, implemented for notifications** | The engine expands all crossed boundaries in one serialised step (`500 ticks → 1 transition`); the service forwards notification actions as the same commands. Notification-button delivery itself needs a device |
| 6 | Rotation and returning from the background preserve the session | **implemented** | The session lives in a process-scoped `SessionController` behind a foreground service and survives configuration changes; not exercised without a device |
| 7 | Screen-off workout continues with ordered cues | **implemented** | `WorkoutService` (foreground, `mediaPlayback`) with a partial wake lock and a media notification; timing derives from the monotonic clock, so a stopped UI thread cannot shift boundaries. Device verification outstanding |
| 8 | A missing TTS voice gives a useful setup path and a sound fallback | **implemented + partially verified** | `SpeechCoach` reports `NoEngine` / `MissingLanguageData`, Voice Studio and Help explain how to install an offline voice, and `CueSoundPlayer` plays synthesised tones instead. The cue-gate behaviour is unit tested; actual TTS output needs a device |
| 9 | Short intervals, long names and overlapping warnings never build a speech backlog | **verified** | `CueGateTest` (8 tests): interval changes always speak and flush, at most one follow-up is queued, countdowns are dropped when they would land late, and a 40-cue burst is throttled |
| 10 | Audio interruption and headphone disconnection follow the setting | **implemented** | `AudioFocusController` + `AudioManager.AUDIOFOCUS_*` handling implemented for pause / keep-timing-mute / duck, and `AUDIO_BECOMING_NOISY` routes to `onHeadphonesDisconnected()` honouring the headphone preference. Needs a device with a headset |
| 11 | Import/export round trips preserve structure and settings | **verified** | `BackupAndCueTest` — every one of the 15 starter templates survives encode → decode with identical step lists and totals; the backup file carries workouts, folders, sessions, voice profiles, labels and reminders |
| 12 | Invalid imports fail safely without touching existing data | **verified** | `BackupAndCueTest` rejects a foreign JSON document and a corrupt plan; `BackupRepository` validates the whole file and previews before writing anything |
| 13 | Database migration preserves workouts and session history | **verified on real SQLite** | `DatabaseMigrationTest` rebuilds the v1 schema from Room's exported schema, applies the shipped `PulseMigrations.ONE_TO_TWO` statements through JDBC, and asserts every row survived |
| 14 | Force-stop, process loss and reboot are distinguished, never shown as uninterrupted | **implemented** | Sessions are written start-and-finish, heartbeated every 15 s, and any session still marked `RUNNING` at startup is closed as `INTERRUPTED` with the heartbeat as its last known time; `BootReceiver` restores reminders and shortcuts. Not additionally unit tested (it needs Room on device) |
| 15 | TalkBack, large text, landscape, light/dark keep controls usable | **partially verified** | Contrast is measured for all three themes (`docs/CONTRAST.md`, 34 pairs, all passing); 48 dp targets, non-drag reordering, phase labels, tabular numerals and a high-contrast switch are implemented. TalkBack itself needs a device |
| 16 | Core flows work in airplane mode; offline speech with a locally installed voice | **verified structurally, implemented otherwise** | The app declares no network permission and links no networking code, so nothing can depend on connectivity; the offline-voice install path is documented in Voice Studio and Help. Offline playback needs a device |
| 17 | The APK installs, launches, runs a workout, saves the result and reopens it | **not possible here** | No emulator or physical device exists in this environment. The debug APK is built and debug-signed (verified with `apksigner`); the install-and-smoke sequence is documented in the README for a machine with a device |

## What that means in practice

- The **timer itself** — the part where a bug is expensive — is verified exhaustively on the JVM,
  including the concrete numbers in cases 1–5 and 9.
- The **data layer** is verified against a real SQLite database (case 13) and real serialization
  (cases 11–12).
- The **platform integration** (foreground service, notifications, audio focus, vibration, TTS,
  widgets, TalkBack) is implemented against the documented APIs and compiles, but has not been
  observed running. That is the honest boundary of what this environment can prove.
