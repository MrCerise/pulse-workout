# Acceptance cases

The seventeen acceptance cases from the project brief, each mapped to the evidence that actually
exists. Statuses are honest: **verified** means an automated test ran and passed, **implemented**
means the code path exists but only a device can exercise it, and **not possible here** means the
environment lacks what the check requires.

- [The seventeen cases](#the-seventeen-cases)
- [What that means in practice](#what-that-means-in-practice)
- [v1.4.2 design-refactor checks](#v142-design-refactor-checks)
- [v1.6.3 editing and control-shape checks](#v163-editing-and-control-shape-checks)
- [v1.4.4 audio-ducking checks](#v144-audio-ducking-checks)
- [v1.7.2 standalone and pairing checks](#v172-standalone-and-pairing-checks)
- [v1.7.3 naming, branding and language checks](#v173-naming-branding-and-language-checks)

For how these checks are run in CI and locally, see [VERIFICATION.md](VERIFICATION.md).

## The seventeen cases

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
| 10 | Audio interruption and headphone disconnection follow the setting | **implemented + partially verified** | `AudioFocusController` + `AudioManager.AUDIOFOCUS_*` handling implemented for pause / keep-timing-mute / duck, and `AUDIO_BECOMING_NOISY` routes to `onHeadphonesDisconnected()` honouring the headphone preference. Since v1.4.4 a `LOSS_TRANSIENT_CAN_DUCK` event lowers the background audio and keeps coaching, and the ducking curve that decision feeds is unit tested (`DuckingTest`, 17 tests). Focus negotiation itself still needs a device with a headset |
| 11 | Workout serialization preserves structure | **verified** | `PlansAndCueTest` — every starter template survives encode → decode with identical step lists and totals |
| 12 | Invalid imports fail safely without touching existing data | **verified** | the JSON importer validates before writing and previews the parsed plan; a corrupt document is rejected, never stored |
| 13 | Database migration preserves workouts and session history | **verified on real SQLite** | `DatabaseMigrationTest` rebuilds the v1 schema from Room's exported schema, applies the shipped `PulseMigrations.ONE_TO_TWO` statements through JDBC, and asserts every row survived |
| 14 | Force-stop, process loss and reboot are distinguished, never shown as uninterrupted | **implemented** | Sessions are written start-and-finish, heartbeated every 15 s, and any session still marked `RUNNING` at startup is closed as `INTERRUPTED` with the heartbeat as its last known time; `BootReceiver` restores reminders and shortcuts. Not additionally unit tested (it needs Room on device) |
| 15 | TalkBack, large text, landscape, light/dark keep controls usable | **partially verified** | Contrast is measured for all three themes (`docs/CONTRAST.md`, 125 pairs, all passing) and the rules behind it are written down in `docs/DESIGN.md`; every interactive element exposes a 48 dp touch target, every icon-only control carries a content description and a tooltip, colour is never the only signal, numerals are tabular so nothing reflows, and the high-contrast and reduced-motion switches are honoured by the design system. TalkBack itself needs a device |
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

## v1.4.2 design-refactor checks

The visual refactor has its own pass/fail criteria, all of which are machine-checkable and were run:

| Check | How it is checked | Result |
| --- | --- | --- |
| One design reference, applied everywhere | `docs/DESIGN.md` states the reference (Signal) and the rules; every screen imports its styling from `ui/components` | **verified** — no screen constructs a button, card, field or chip of its own |
| No hardcoded styles | Token usage is the only path: `LocalPulseColors` / `LocalPulseDimens` / `LocalPulseShapes`; the raw palette is `internal` to `ui/theme` | **verified** — the screens contain no colour literals, and spacing is `LocalPulseDimens.current` |
| Centralised tokens | `colors.xml` ↔ `Palette.kt` name-for-name comparison | **verified** — `scripts/contrast_check.py` exits 2 on any drift |
| Readable contrast in dark, OLED and light | 125 measured pairings | **verified** — exit 0, see `docs/CONTRAST.md` |
| Forbidden traits removed | Gradient buttons, ring gradients, phase washes, glow tiles, pill chips and 28 dp cards are gone | **verified** — none of `brandGradient`, `ringGradient`, `phaseWash`, `activeGlow` or `GradientActionButton` exist in the tree |
| Functionality preserved | No engine, data, session, health or parser file was touched by the refactor; only `ui/`, resources and documentation changed | **verified by diff** — `engine/` and the non-UI app packages are untouched |
| Every route refactored | All eleven destinations plus the four settings sub-screens use `PulseTopBar` and the shared component set | **verified** — `scripts/kotlin_xref_gate.py` reports 0 problems across every screen |
| Component call sites valid | Every named argument passed to a `ui/components` composable exists on its declaration | **verified** — 43 components, 0 unknown arguments |
| Resources and locale parity | `scripts/check_ui.py` | **verified** — 612 resource names resolve, 397 EN / 397 FR app strings and 63 / 63 on the watch, vectors parse |

Compilation, lint and the APK builds remain the CI workflow's job (no Android SDK or JDK in this
environment); the local gates above are the ones that can run before it.

## v1.6.3 editing and control-shape checks

Two defects were closed in v1.6.3: *Edit workout* created a second workout instead of changing the one
it was opened from, and the chips you tap in the builder and in the library's filters were pills. The
first has an automated test for the part that broke (the route that carries the id) and needs a device
for the rest; the second is a shape decision, so it is checked against the design rules.

| Check | How it is checked | Result |
| --- | --- | --- |
| The builder destination declares an optional `workoutId` | `BuilderRouteTest.the builder destination declares an optional workout id` | **verified** |
| *Edit* puts the workout's id in the route; *new* reaches the same destination without one | `BuilderRouteTest.editing a workout puts its id in the route`, `a new workout reaches the same destination with no argument` | **verified** |
| An id a URI query cannot carry survives the trip | `BuilderRouteTest.an id the query cannot carry is escaped rather than dropped` | **verified** |
| Saving an edited plan updates the row: same id, `createdAt`, folder and tags | `WorkoutRepository.save` upserts on `plan.id`, keeps `existing.createdAt` / `folderId`, and only rewrites tags the caller actually reports | **implemented** — compiling and device-verifying is CI's job; no Robolectric or Room-in-JVM harness here |
| Undo/redo cannot rewrite plan metadata it did not touch | `BuilderSnapshot` holds the whole `WorkoutPlan`, so no field is reconstructed from a subset | **verified by construction** |
| A tappable chip is a rectangle | `PulseChoiceChip` draws `LocalPulseShapes.current.control` (6 dp); `docs/DESIGN.md` §1.3 states the rule by function, not by size | **verified** — the only remaining `shapes.chip` call sites are labels, badges and tracks |
| A builder option is readable, not ellipsized | `OptionRow` lays its choices out two to a row, the same grid `CategoryPicker` uses | **verified by construction** — seven equal columns on a 360 dp phone leave ~5 dp of text per option |
| Material's own surfaces follow the tokens | `PulseTheme` maps `Shapes(extraSmall = shapes.panel, …)`; `MaterialTheme.shapes.extraSmall` is what a `DropdownMenu` draws its container with, and it used to be the 999 dp pill | **verified** — the library's sort, new-workout and overflow menus now take the 6 dp panel radius like `PulseDropdown` states |
| Documentation and code agree | `scripts/check_ui.py`, `scripts/kotlin_xref_gate.py` | **verified** — 0 problems |

## v1.4.4 audio-ducking checks

Ducking was added in v1.4.4 together with the wiring that makes background audio play at all. The
volume curve is pure Kotlin over an injected clock, so it is verified on the JVM; the audible result
still needs a device.

| Check | How it is checked | Result |
| --- | --- | --- |
| Ducking can be switched off | `DuckingTest.ducking off leaves the music at full volume even while the coach speaks` | **verified** — scale stays 1.0 with speech running |
| The drop, hold and recovery are the configured shape | `DuckingTest` — ramp, hold, fade-up and settle assertions with an explicit clock | **verified** — 1.0 → 0.30 over the fade, held for the hold, back to 1.0 |
| A long announcement never lets the music back in early | `an open ended duck lasts as long as the coach is talking` | **verified** — still ducked after 60 s with no scheduled change |
| Back-to-back cues do not pump the volume | `a second utterance before the hold ends keeps the music down`, `ducking again mid recovery drops from the current volume instead of snapping` | **verified** |
| A cue tone ducks for exactly its own length | `a cue tone ducks for its own length and then recovers`, `a cue tone during speech never shortens the duck` | **verified** — driven by `CueSoundPlayer.durationMillis` |
| A 40-cue burst always returns to full volume | `a forty cue burst never leaves the music ducked afterwards` | **verified** |
| Out-of-range depths cannot amplify or invert | `out of range levels are clamped instead of amplifying or inverting` | **verified** |
| Settings changed mid-workout apply without a restart | `SessionController` pushes every `UserPreferences` emission into `MusicController.configure` | **implemented** — needs a device to hear |
| Ducking follows the real speech engine | `SpeechCoach` utterance counting (`onStart` / `onDone` / `onStop` / `onError`) plus a watchdog from the cue gate's estimate | **implemented** — TTS callbacks need a device |
| Background audio starts, pauses, resumes and is released with the session | `SessionController.startWorkout` / `syncMusicWithEngine` / `teardown` → `MusicController` | **implemented** — ExoPlayer output needs a device |
| Volume keys control the workout when asked to | `MainActivity.onKeyDown` behind the `volume keys control the session` preference | **implemented** — needs a device |

## v1.7.2 standalone and pairing checks

v1.7.2 makes the watch app usable with no phone at all, and keeps pairing as an upgrade rather than a
requirement. The rules that could silently corrupt something — which copy of a workout wins, what a
finished session records, what an undelivered log does — are pure Kotlin or covered by JVM tests; the
DataStore plumbing and the data layer itself need a watch.

| Check | How it is checked | Result |
| --- | --- | --- |
| A watch that has never been paired has workouts to run | `WatchWorkoutStore.seedBundledWorkoutsIfNeeded()` seeds `StarterWorkouts` when no manifest has ever been applied and nothing is stored; the catalogue itself is verified | **verified for the catalogue, implemented for the store** — `:library:test` expands, validates and id-checks every template; the DataStore seeding needs a watch |
| The seeded workouts are *the same* workouts as the phone's | Both APKs derive ids from the same seeds: `StarterWorkoutsTest` pins the MD5-derived `builtin-…` ids, so the library manifest reconciles instead of duplicating | **verified** |
| Seeding cannot produce a hybrid library | `applyLibrary` sets `library_synced`, and only that flag stops re-seeding; a watch updated from 1.7.1 already has a stored library, which also stops it | **implemented** — needs a device to exercise the two upgrade paths |
| A session finished with no phone is still recorded | `WatchSessionRecorder` writes the local history before handing the log to the link, and a link that throws cannot prevent it (`WatchSessionRecorderTest`) | **verified** for the fan-out; the DataStore write needs a watch |
| The history is bounded and ordered | `mergeWatchHistory` — newest first, one entry per session id, capped at `WATCH_HISTORY_LIMIT`, and an over-long stored value is truncated on read (`WatchHistoryTest`) | **verified** |
| A corrupt history cannot block the app | `WatchHistoryCodec.decode` returns an empty list for unreadable, truncated or missing values (`WatchHistoryTest`) | **verified** |
| Turning the phone link off stops all traffic | `WatchPreferences.syncEnabled` gates `WearSyncLink`'s inbound handlers, `sendCommand`, `requestSync`, `onLiveSnapshot` and `deliverPendingLogs`; queued logs are preserved | **implemented** — the guards compile and are reasoned about; only a device can show the absence of traffic |
| An undelivered session is retried, not lost | The link retries the persisted queue on a timer and on every peer change, and the phone acknowledges with `MESSAGE_LOG_ACK` | **implemented** — needs both devices |
| Two devices never coach the same workout | Home's tap asks once, then sends `WatchCommandKind.STOP` to the phone before starting locally | **implemented** — needs both devices |
| The two apps stay compatible across the update | No payload changed shape; `PROTOCOL_VERSION` stays 1, and `SyncProtocolTest` still covers the codecs | **verified by construction** |
| Home and Settings agree about sync state | `watchSyncStatus()` is the only producer of that line, used by both screens | **verified by construction** |
| The catalogue is shared, not copied | `:library` is the only definition of the fifteen templates; `check_ui.py` and `kotlin_xref_gate.py` scan the module and PR CI runs `:library:test` | **verified** |

## v1.7.3 naming, branding and language checks

v1.7.3 renames the app to **Pulse Workout** everywhere the user sees a name, draws that mark smaller
inside the launcher icon, and lets the language be chosen instead of inherited from the device. None of
it is engine behaviour, so the evidence is the resource gates plus new JVM tests; the parts that are
only true once a launcher or a device renders them say so.

| Check | How it is checked | Result |
| --- | --- | --- |
| Every surface carries the name | `scripts/check_brand.py` reads `app_name` out of `values/strings.xml` and refuses a launcher label that is not it; the former all-caps wordmark is a forbidden pattern in the phone and watch string tables | **verified** |
| The launcher mark is centred at the documented size | `check_brand.py` derives the group scale from the vector and the viewport, and compares both against `docs/DESIGN.md` | **verified** — 50 dp of mark in the 108 dp adaptive viewport, foreground and monochrome alike, on phone and watch |
| The icon reads smaller next to the others on a home screen | — | **not possible here** — the mask, the icon size and the neighbouring apps belong to the launcher |
| A chosen language names a locale the app actually packages | `AppLanguageTest` — each offered tag is well-formed, matches a `values-…` folder, and no two entries share a tag | **verified** |
| A stored choice cannot be orphaned by a rename | `AppLanguageTest` pins the enum names that are what DataStore holds | **verified** for the contract; the DataStore round trip needs a device |
| A translation never invents a key | `AppLanguageTest` compares every `values-*/strings.xml` against the English source table | **verified** |
| English and French stay complete | `scripts/check_ui.py` exact key parity | **verified** |
| The shell has no language-blind text | The bar, sidebar groups and breadcrumb titles resolve through `stringResource` inside `Localized`, so they follow the choice like the screens under them | **verified** for the wiring (each key exists in both tables); the visible result needs a device |
| The strings the language picker adds are translated | three keys in `values/strings.xml` and `values-fr/strings.xml`; the other shipped tables stay Crowdin's to fill | **verified** for parity; the picker is complete for French |
| A re-entry from a notification or widget keeps the chosen language | the choice is applied to the application configuration on API 33+, and `MainActivity` declares `locale\|layoutDirection` in `configChanges`, so the language change is handled in place rather than by recreating the activity | **implemented** — needs a device to see |
| A language restored from a backup is complete on the new device | `PulseApp.onCreate` re-applies the stored choice to `LocaleManager`, and the write is skipped when the platform already agrees | **implemented** — needs a device, and a restore to exercise |
| A finishing session survives a link that throws | `WatchSessionRecorder` contains both of its calls, asserted by `WatchSessionRecorderTest` including a live-snapshot failure | **verified** |
