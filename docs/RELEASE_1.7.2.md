# v1.7.2 release verification

Version **1.7.2**, version code **18**, minimum SDK 26 on both APKs. No database migration, no new
permissions on either app, no change to any sync payload (`PROTOCOL_VERSION` stays **1**, so a 1.7.1
phone and a 1.7.2 watch keep talking to each other).

## Changes

- New `:library` module (pure Kotlin, `api(project(":engine"))`): the bundled starter catalogue,
  moved out of `app/src/main/java/com/pulse/intervalcoach/data/StarterWorkouts.kt`. Both APKs
  depend on it, so the phone and the watch offer the same fifteen workouts from one definition.
  `:library:test` proves the catalogue expands, passes the engine's own validation, keeps its
  documented ids and declares its open-ended members.
- `:wear`: the watch seeds that catalogue into its own store (`WatchWorkoutStore.seedBundledWorkoutsIfNeeded`)
  when it has never received a phone manifest, so an unpaired watch is usable on first launch. The
  `library_synced` flag is the one-way switch that stops re-seeding once a phone owns the library,
  including for a watch updated from 1.7.1 (whose stored library already exists).
- `:wear`: new local history (`history/WatchSessionRecord.kt`, `history/WatchHistoryStore.kt`) with
  a 20-session cap and a `WearHistoryScreen` that names where each record lives. The recorder
  (`session/WatchSessionRecorder.kt`) writes the history *and* hands the log to the phone link.
- `:wear`: `WatchPreferences.syncEnabled` (local, never mirrored) with a Settings switch; when it is
  off, `WearSyncLink` sends nothing and applies nothing, while the pending-log queue is preserved.
- `:wear`: `WearSyncLink` retries queued logs on a timer; Home and Settings share
  `watchSyncStatus()`; tapping a workout during a phone session confirms once and sends `STOP`
  before starting locally.
- Static gates: `check_ui.py` and `kotlin_xref_gate.py` scan `library/src/main/kotlin`; PR CI runs
  `:library:test` together with `:engine:test :sync:test :app:testDebugUnitTest :wear:testDebugUnitTest`.

## Automated checks

Run locally (no Android SDK needed):

```bash
python3 scripts/check_ui.py          # 385 app + 63 wear strings, EN/FR parity, resources resolve
python3 scripts/check_brand.py
python3 scripts/kotlin_xref_gate.py  # 0 problems
python3 scripts/contrast_check.py    # rewrites docs/CONTRAST.md; no token changed here
git diff --exit-code -- docs/CONTRAST.md
```

With `pip install tree-sitter tree-sitter-kotlin`, `python3 scripts/kotlin_parse_gate.py` parses all
91 Kotlin files with 0 syntax errors.

CI on pull request #25 runs the four gates above plus
`:engine:test :sync:test :library:test :app:testDebugUnitTest :wear:testDebugUnitTest`. Pushing the
`v1.7.2` tag builds four signed APKs (phone/wear × debug/release) and attaches them to the GitHub
release.

What the unit tests cover for this change:

- the bundled catalogue expands, validates and is id-stable (`:library:test`);
- the watch history keeps newest-first order, replaces a repeated session id, caps at 20, survives
  a round trip and refuses a corrupt or truncated stored value (`:wear:testDebugUnitTest`);
- a finished watch session is written to the local history before it is handed to the link, and a
  link that cannot deliver cannot prevent the local record (`WatchSessionRecorderTest`);
- the existing watch session lifecycle (start, tick, control, finish, log contents) is unchanged and
  still passes (`WatchSessionControllerTest`).

## Maintainer/device checks

Not claimed as done by the source changes alone — no watch or phone exists in the environment the
change was written in:

- Install the watch APK **without ever pairing a phone**: confirm the library appears with the
  fifteen starter workouts, that a workout runs with haptic cues, that the summary says *Saved on
  this watch*, and that History lists it as *On this watch*.
- Turn *Sync with phone* off and on while a workout runs: no live session should appear on the phone
  while it is off, and the finished session should land in the phone's history once it is on.
- Pair afterwards and open the phone app: the watch's library should be replaced by the phone's
  (the seeded workouts reconcile by id, they do not duplicate), settings should follow the phone,
  and the sessions queued from the solo period should arrive in the phone's history.
- With a phone session running, tap a workout on the watch: the watch should ask once, then the
  phone's session should stop (recorded as stopped early) and the workout should run locally.
- Update a paired watch from 1.7.1 to 1.7.2: the mirrored library must be preserved, nothing may be
  re-seeded, and the 1.7.1 phone must keep syncing (protocol version unchanged).

---

See also: [CHANGELOG.md](../CHANGELOG.md#172--2026-10-15) · [ARCHITECTURE.md](ARCHITECTURE.md) · [RELEASING.md](RELEASING.md)
