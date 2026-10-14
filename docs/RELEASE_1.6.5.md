# v1.6.5 release verification

Version name **1.6.5**, version code **13**. Minimum SDK remains 26; no database migration.
The obsolete volume-key preference is ignored on upgrade. Existing workouts/settings are retained.

## Automated checks

- `python3 scripts/contrast_check.py` — token parity and contrast report.
- `python3 scripts/kotlin_parse_gate.py` — Kotlin syntax gate.
- `python3 scripts/kotlin_xref_gate.py` — project cross-reference gate.
- `./gradlew :engine:test :app:testDebugUnitTest` — CI on the release PR.
- Tag workflow: debug and minified, signed release APK builds.
- New regressions: queued/flushed/stopped/rejected speech IDs; disabled/retriggered/overlapping
  ducks; controller sampling with no local music, watchdog and cleanup; empty/extreme share stats.

## Device smoke checklist (not executable in the Arena sandbox)

These remain hardware/manual checks, not claims of a completed device test:

1. Upgrade from 1.6.4 with the old volume-key option enabled. While running, paused, in settings,
   and between cues, tap/hold both volume keys: only media volume changes; no interval skips.
2. Pick a local audio file. Test speech, countdowns, long chimes, rapid Next, pause/resume and Stop.
   Check lowering and restoration at 0%, 30%, 100%, and with ducking disabled. Check the first cue
   during fade-in, an unreadable/revoked file, and restarting while a file is loading.
3. With no local file, play music in an external player. Test Voice Studio and live workouts,
   screen on/off and Bluetooth. It should receive MAY_DUCK during cues and regain focus afterward.
   Test two players: external duck depth is Android/player-controlled, not PULSE-controlled.
4. Interrupt with a call/navigation prompt. Confirm the configured interruption behavior and
   restoration; a user-muted coach must stay muted when focus returns. Unplug headphones.
5. Share completed, early-ended, empty, hour-long, French and emoji/long-title workouts. Inspect
   the full PNG for clipping; confirm the date belongs to the session, not the export. Share to
   Files and a messaging app. Cancel/retry and tap repeatedly; no duplicate chooser or crash.
6. Low-storage/share-target failure: show a retryable error. The summary must remain usable.
7. Install the signed release over the prior signed APK to confirm signing-key continuity.

## Known platform limits

Android audio focus is cooperative. Some music/speech players pause, ignore duck requests, or use
system-defined attenuation; PULSE cannot force another app to an exact percentage. The depth
slider affects only a file selected in PULSE. Android 15+ requires the app to be foreground or have
its workout foreground service running to request audio focus. Physical audio quality, Bluetooth
latency and OEM focus behavior need the device checks above before a store rollout.
