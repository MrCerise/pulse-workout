# Architecture

PULSE is four Gradle modules and a set of dependency-free Python gates. The split exists so the parts
where a bug is expensive — the timer and the phone↔watch protocol — can be tested exhaustively on
the JVM, without an emulator and without Android.

```
:engine   pure Kotlin/JVM timing engine, no Android dependencies
:sync     pure Kotlin/JVM phone↔watch protocol: payload formats, codecs, event mapping
:app      the Android application (Compose, Room, DataStore, Media3, WorkManager, AppWidgets)
:wear     the Wear OS application (Compose for Wear OS, DataStore, the Wear data layer)
```

- [Modules](#modules)
- [The timing engine](#the-timing-engine)
- [How the timer works](#how-the-timer-works)
- [The Wear OS link](#the-wear-os-link)
- [Application layers](#application-layers)
- [Threading and process model](#threading-and-process-model)
- [Constraints we hold ourselves to](#constraints-we-hold-ourselves-to)

## Modules

| Path | What it is |
| --- | --- |
| `engine/` | `Model.kt` — interval types, the plan tree, validation and timeline expansion. `TimerEngine.kt` — the state machine, with the clock injected. 29 tests in `engine/src/test`. |
| `sync/` | The phone↔watch protocol as pure Kotlin: `Sync.kt` defines every payload (library manifest, preferences, live session snapshot, command, finished-session log), the JSON codecs, and the mapping from engine events to history events. Protocol-codec round-trip tests in `sync/src/test`. |
| `app/` | Everything Android: screens, theme, components, persistence, health integration, the foreground service that keeps a session alive, widgets, background work, and the phone half of the Wear link (`wear/WearSyncManager.kt`). |
| `wear/` | The Wear OS app: the same engine running on the watch, the same design tokens mirrored for a round screen, and the watch half of the Wear link (`sync/WearSyncLink.kt`). Session-lifecycle tests in `wear/src/test`. |
| `scripts/` | `contrast_check.py` (regenerates [`CONTRAST.md`](CONTRAST.md) and asserts the watch palette mirror), `check_ui.py` (resources, vectors, EN/FR parity per module), `check_brand.py` (phone and watch brand marks), `kotlin_parse_gate.py` and `kotlin_xref_gate.py` (dependency-free Kotlin syntax and cross-reference gates), `capture-screenshots.sh`. |

Inside `app/src/main/java/com/pulse/intervalcoach/`:

| Package | Responsibility |
| --- | --- |
| `audio/` | Cue tones, haptics, speech through the device TTS engine, and `CueGate` — the anti-backlog filter every announcement passes through. `Ducking.kt` is the music-ducking curve. |
| `data/` | Room database and migrations, DataStore preferences, repositories, the starter templates, the `pulse.workouts` JSON parser, backup/export, and `Categories.kt` — the category registry. |
| `health/` | `HealthHub` fronting Health Connect (primary) and Google Fit (best-effort secondary): reads live HR and steps, writes finished sessions. |
| `session/` | `CueScript` (what gets said, when), `SessionController` (the live session state), `WorkoutService` (the foreground service) and background music. |
| `ui/theme/` | The design system: `Palette.kt` (raw tokens), `Tokens.kt` (colour/space/radius/elevation/motion locals), `Typography.kt`, `Theme.kt`. |
| `ui/components/` | The shared component library — `Foundation` (including `PulseScaffold`), `Controls`, `Indicators`, `DataDisplay`, `Feedback`, `Navigation`. |
| `ui/` | Screens: Today, Workouts, the builders, player, summary, Progress, Settings, Health, Welcome and JSON/AI import. |
| `widget/` | Home-screen widgets and dynamic shortcuts. |
| `work/` | WorkManager jobs: the weekly backup and session reminders. |

Full design-system documentation lives in [`DESIGN.md`](DESIGN.md); the module map above is also
summarized in the [README](../README.md#repository-layout).

## The timing engine

`engine/` is plain Kotlin with **no Android dependencies**, and it receives its clock through the
constructor:

```kotlin
interface MonotonicClock { fun elapsedRealtime(): Long }

val engine = TimerEngine(plan, clock = SystemClockBasedClock())
```

That single decision is why every acceptance case can be tested on the JVM: a fake clock can sit
still, jump an hour, or run at 50× without touching a device.

The plan is a **tree**, not a flat list:

- a workout holds `steps`, and a step is either an interval or a repeat group;
- repeat groups nest (depth limit 8) and carry a `repeat` count and optional name;
- validation and **timeline expansion** happen in `Model.kt`, with hard limits (10,000 expanded
  intervals, 24 hours of timed content per workout) applied *before* expansion so nesting cannot
  produce an unbounded allocation;
- open-ended (manual) intervals count up and wait for the user, and report totals as a minimum.

## How the timer works

- **One monotonic clock, no drift.** The engine reads `SystemClock.elapsedRealtime()` through the
  injected `MonotonicClock`. Step boundaries are computed from the anchor time of the current step,
  never by accumulating tick deltas, so a 20-minute session cannot drift and a wall-clock (NTP or
  timezone) change cannot move it.
- **Pause keeps the remaining time.** Pausing freezes the elapsed value; resuming re-anchors the
  clock. A pause of any length resumes exactly where it stopped.
- **Crossing several boundaries at once is handled.** A tick that jumps past multiple intervals
  completes each of them in order and emits one event per interval, so rapid taps cannot produce
  duplicate transitions.
- **Open-ended intervals** — AMRAP, stopwatch, strength — count up and wait for the user; totals
  report themselves as a minimum rather than inventing an end time.
- **Foreground service.** A workout runs in `WorkoutService`
  (`foregroundServiceType="mediaPlayback"`) with a media notification, so timing and cues continue
  with the screen off or with another app in front. Android can still kill the process under extreme
  memory pressure; the session is then reported as **interrupted** in history rather than being
  marked complete.
- **Cue gate.** A Tabata block produces more announcements than a voice can finish, so every cue
  passes through `CueGate`: interval changes always speak and flush, at most one follow-up cue may
  be queued, and countdowns are dropped when they would run past the next boundary.
- **Sound fallback.** If the device has no TTS engine — or no voice for the selected language —
  PULSE plays synthesised tones and tells you, instead of silently dropping cues.

## The Wear OS link

The phone app and the watch app are two APKs with the **same application id** — that is what the
Wear data layer uses to find peers — built around the shared `:engine` and `:sync` modules. The
watch is a real client, not a notification mirror: it runs the same engine, with the same
monotonic anchoring, on its own CPU.

```
phone :app                                     watch :wear
──────────────────────                         ──────────────────────
WorkoutRepository ──▶ data items ────────────▶ WatchWorkoutStore (library mirror)
PreferencesRepository ─▶ data item ─────────▶ WatchPreferences (theme, cues)
SessionController.snapshot ─▶ messages ────▶ WearSyncLink.remoteSession (live mirror)
WearSyncManager.remoteWatchSession ◀─ messages ─ WatchSessionController (live mirror)
WearSyncManager.sendCommand ──▶ messages ───▶ WatchSessionController.applyCommand
SessionRepository.recordWatchSession ◀─ log ─ WatchSessionController (finished session)
```

- **Durable state rides data items; point-in-time state rides messages.** The workout library,
  its manifest and the mirrored preferences are data items, which the platform replicates and
  queues across disconnects. Live snapshots (~1 Hz) and commands are messages, which never queue.
  A finished watch session is important, so it is the exception: the watch keeps undelivered logs
  queued in its DataStore and retries until the phone acknowledges.
- **Whoever runs the session owns it.** The device with the engine applies commands and records
  the log; the other device is a remote control and a display. This is why the phone can pause the
  watch's workout and the watch can pause the phone's, with no election protocol at all.
- **The watch's history is the phone's history.** A watch session arrives as a `SessionLog`, is
  stored verbatim (same entity shape, same event kinds) and — when Health Connect sync is enabled
  — is written there too, exactly as a phone session would be.
- **Protocol versions gate everything.** Every payload carries `PROTOCOL_VERSION`; a peer that
  does not understand a payload drops it rather than misreading it, so the two APKs can be
  updated independently.
- **The theme is mirrored by value, not by reference.** An application module cannot be shared
  between APKs, so the watch ships `WearPalette.kt` with the same constants; `contrast_check.py`
  fails the build if the two ever drift (see [`CONTRAST.md`](CONTRAST.md)).

## Application layers

```
UI (Compose)  ──observes──▶  SessionController / ViewModels  ──▶  Repositories
     │                              │                                 │
     │                              ▼                                 ▼
     │                        TimerEngine (engine)              Room + DataStore
     │                              │                                 │
     │                              ▼                                 ▼
     └──────────────▶  WorkoutService + audio/TTS  ◀────  HealthHub (Health Connect / Fit)
```

- **State flows one way.** Screens observe the session and repository state; they do not own timing
  state. `UserPreferences` is a DataStore flow pushed into the running session, which is why
  changing ducking depth or verbosity mid-workout applies without a restart.
- **Sessions are written start-and-finish** and heartbeated every 15 s. Any session still marked
  `RUNNING` at startup is closed as `INTERRUPTED` with its last heartbeat, so a force stop, a
  process death and a reboot are never shown as an uninterrupted session.
- **Storage is local and versioned.** Room holds workouts, folders, sessions, voice profiles, labels
  and reminders; schema changes ship with a migration, an exported schema under `app/schemas/` and a
  `DatabaseMigrationTest` that replays the shipped SQL against real SQLite.

## Threading and process model

- The engine is driven from a coroutine tick loop; its public surface is called serially, which is
  what makes "500 ticks produce one transition" true under rapid input.
- Player and audio access is confined to the main thread — this is deliberate and load-bearing for
  cue ducking (an ExoPlayer touched from two threads produced exactly the classes of bug v1.6.5
  fixed).
- Notification actions, widget taps and shortcuts all funnel through the same commands the UI uses,
  so there is one code path per user intent.
- `Save`/`Discard`/`Cancel` on a dirty builder draft survives configuration change and process
  death; drafts are stored in app-private paths only.

## Constraints we hold ourselves to

These are design invariants, not preferences. A change that breaks one of them will be asked to
change in review (see [CONTRIBUTING.md](../CONTRIBUTING.md)):

1. **The engine stays Android-free and clock-injected.** New timing behaviour needs a test in
   `engine/src/test`.
2. **No network code in PULSE itself.** No analytics, no crash reporting, no ads, no account. The
   `INTERNET` permission appears in the merged manifest only because Play services libraries declare
   it for their own platform communication.
3. **Colours, spacing and shapes come from tokens**, never from literals in a screen.
4. **User-visible text comes from string resources**, in English and (via Crowdin) every shipped
   locale.
5. **Untrusted input is bounded and validated before anything is written** — JSON imports,
   backups, deep links, clipboard content.
