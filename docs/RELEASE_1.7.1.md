# v1.7.1 release verification

Version **1.7.1**, version code **17**, minimum SDK 26 on both APKs. No database migration, no new
app permissions on the phone. The watch APK adds `WAKE_LOCK`, `VIBRATE` and the watch feature flag.

## Changes

- New `:sync` module: the phone↔watch protocol as pure Kotlin (payloads, codecs, event mapping),
  with round-trip tests (`:sync:test`).
- New `:wear` module: a Compose for Wear OS app running the shared `:engine`, with the mirrored
  Signal palette, a DataStore-backed library/preferences store, haptic cue output and the watch
  half of the link. Session-lifecycle tests run on the JVM with a fake clock
  (`:wear:testDebugUnitTest`).
- Phone app: `WearSyncManager` pushes the library manifest, per-workout items and preferences;
  mirrors the live session and applies watch commands; stores finished watch sessions into
  history (and Health Connect when enabled); a "workout running on your watch" card on Today and
  a live remote-control screen.
- Static gates extended: wear resources and EN/FR parity (`check_ui.py`), watch brand marks
  (`check_brand.py`), import resolution across all modules (`kotlin_xref_gate.py`), and a
  palette-mirror assertion in `contrast_check.py` (see [CONTRAST.md](CONTRAST.md)).

## Automated checks

Run `python3 scripts/check_ui.py`, `python3 scripts/check_brand.py`,
`python3 scripts/kotlin_xref_gate.py` and `python3 scripts/contrast_check.py`.
PR CI runs `:engine:test :sync:test :app:testDebugUnitTest :wear:testDebugUnitTest`. Pushing
`v1.7.1` builds four signed APKs (phone/wear × debug/release) and attaches them to the GitHub
release.

## Maintainer/device checks

- Install the phone APK (1.7.1 over 1.7.0) and the wear APK on a paired watch; confirm both
  appear as one app with a watch launcher icon.
- Open the watch app: confirm the library populates from the phone (favourites first), that
  editing/renaming/deleting a workout on the phone propagates, and that "Sync now" re-requests.
- Run a workout on the watch: confirm haptic cues at interval changes and countdown ticks, timing
  survives a wrist turn, and the summary reports the session saved to the phone's history — then
  verify it appears in the phone's Progress/History with its event trail.
- Run a workout on the phone: confirm the watch shows the live session and that pause/skip/+15 s
  from the wrist control the phone's engine and spoken cues.
- Change the phone theme (dark / pure black / high contrast): confirm the watch follows on the
  next sync.

These external device checks are not claimed as completed by the source changes alone.

---

See also: [CHANGELOG.md](../CHANGELOG.md#171--2026-10-15) · [ARCHITECTURE.md](ARCHITECTURE.md) · [RELEASING.md](RELEASING.md)
