# Data, privacy and backups

PULSE is offline-first. This page states exactly what is stored, where it lives, what can leave the
device, and how to move it.

- [What is stored](#what-is-stored)
- [Privacy guarantees](#privacy-guarantees)
- [Health data](#health-data)
- [Export and import](#export-and-import)
- [Automatic backups](#automatic-backups)
- [Device backup and transfer](#device-backup-and-transfer)

## What is stored

Everything lives in the app's private storage on the device: workouts and their plans, folders and
tags, session history (including the workout as it was actually run), notes, voice profiles and
preferences. Nothing is cached in a service we run, because there is no service.

| Store | Holds |
| --- | --- |
| Room database | workouts, folders, sessions, voice profiles, labels, reminders |
| DataStore | user preferences (theme, audio, units, health toggles, defaults) |
| App-private files | draft snapshots, own recordings, exported share images (expiring) |

## Privacy guarantees

- **PULSE's own code makes no network calls.** It never opens a socket, never sends analytics and
  never uploads anything. The `INTERNET` permission appears in the merged manifest only because the
  Play services libraries used for Google Fit declare it for their own platform communication;
  Health Connect itself is an on-device IPC API.
- **No analytics SDK, no advertising SDK, no crash reporter, no account, no cloud.**
- **Files you export go only where you send them** through the system file picker.
- **AI features are bring-your-own.** PULSE has no API key and calls no provider; only the prompt
  you choose to share leaves the app, and your provider's own privacy policy applies to it. Clipboard
  content is read only when you tap *Paste response*, never on resume. See
  [WORKOUT_JSON.md](WORKOUT_JSON.md).
- **Translations never touch the runtime.** [Crowdin](https://crowdin.com/project/pulse-workout) is
  build-time tooling; the installed app contains no Crowdin SDK and makes no Crowdin calls.

## Health data

Health data leaves the device only when you authorise it:

- The Health Connect write ("share finished workouts") is a per-app permission you grant and can
  switch off under *Settings → Health* at any time.
- What is written: workout sessions with per-interval segments and the recorded heart rate. Nothing
  else.
- Heart rate read for the player and steps read for the Today screen are read permissions of the
  same kind: granted by you, revocable in Android settings.
- See [SECURITY.md](../SECURITY.md#scope) for what is in scope for a vulnerability report.

## Export and import

- **Export:** *Settings → Data → Export all data…* writes a human-readable JSON file
  (`"format": "pulse.backup", "version": 1`) to a location you choose.
- **Import:** *Import from file…* reads the file, validates it, and shows what it contains
  (*n* workouts, *n* sessions, *n* tags) before writing anything. Imported workouts get fresh IDs
  and an "(imported)" suffix, so nothing is overwritten. Since v1.6.7 the preview and the import
  read the same snapshot, and ID collisions cannot overwrite existing records.
- **Workout exchange** (a different format, for generated or hand-written workouts) is documented in
  [WORKOUT_JSON.md](WORKOUT_JSON.md). It is *not* the internal `WorkoutPlan` JSON and not a
  `pulse.backup` file.
- **Missing media** — an image or audio file that was deleted, or whose permission was revoked — is
  reported when the workout is opened; the workout itself still loads.

## Automatic backups

A weekly WorkManager job writes a dated backup into app storage and keeps the newest eight.
Failures are surfaced in the notification channel, never silently dropped.

## Device backup and transfer

Android's own backup/transfer includes the database and preferences, and excludes the regenerable
cache and recording folders (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`).
