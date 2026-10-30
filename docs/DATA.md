# Data and privacy

PULSE is offline-first. The user-facing policy is maintained in
[`legal/privacy.json`](../legal/privacy.json). Run `python scripts/generate_privacy_policy.py`
after editing it: this generates identical English/French policy text for the phone, watch and
[`privacy/index.html`](privacy/index.html) / [`privacy/fr.html`](privacy/fr.html).
CI checks that these copies match. Publication steps are in [PLAY_COMPLIANCE.md](PLAY_COMPLIANCE.md).

## What is stored

Everything lives in the app's private storage on the device: workouts and their plans, folders and
tags, session history (including the workout as it was actually run), notes, voice profiles and
preferences. Nothing is cached in a service we run, because there is no service.

The phone also stores journey enrollment, the enrollment timezone, preferred training days,
optional reminder time, weekly target history, readiness responses and completed journey attempts.
Journey sessions are distinguished from free workouts and do not enter free workout statistics,
Health Connect export, or the Wear OS link. Android backup includes these local records.
Settings → Delete all sessions clears both session histories, journey completions and readiness
responses; it keeps workouts and the chosen training rhythm.

Running session history can include GPS coordinates, distance, speed and a route. Health Connect
integration only exports completed workouts; PULSE does not read heart-rate or step records.

| Store | Holds |
| --- | --- |
| Room database | workouts, folders, sessions, voice profiles, labels, reminders |
| DataStore | user preferences (theme, language, audio, units, health toggles, defaults) |
| App-private files | draft snapshots, own recordings, exported share images (expiring) |

The chosen language is one of those preferences, and it is deliberately **not** mirrored: it changes
only how this app draws its own strings, never the device locale and never the other app on the
wrist. Nothing about it belongs in a workout payload, so a phone and a watch built from different
versions still agree.

The watch app keeps the same kind of state in its own private storage and nothing else: the
mirrored library and the mirrored preference fields, its own list of the last 20 sessions it ran,
and any finished session it has not delivered to a phone yet. With no phone those session records
stay on the watch and are visible in its History screen, where clearing them is a two-tap action;
uninstalling the watch app removes them with it.

## Privacy guarantees

- **No developer-operated backend, analytics, advertising, crash reporter or user account.** This
  does not mean every platform service stays offline. Inspect the release's merged manifest and
  SDKs rather than inferring Data safety questionnaire answers from the source manifest alone.
- **Wearable Data Layer** transfers library/preferences/session information between phone and watch.
  Google Play services may relay it through Google servers when Bluetooth is unavailable; the
  relay is end-to-end encrypted. Watch Settings → Sync with phone controls this link. Disabling it
  does not delete previously synchronized copies on either device.
- **Android backup** can send the private database and preferences, including workout history and GPS
  routes, to the user's backup provider according to Android settings.
- **Text-to-speech** uses the selected Android engine, which may process coaching text online under
  its own settings and policy.
- **Workout files and summary images go where you send them** through the file picker or sharing UI.
  Summary images can include session statistics and a route map.
- **AI features are bring-your-own.** PULSE has no API key and calls no provider; only the prompt
  you choose to share leaves the app, and your provider's own privacy policy applies to it. Clipboard
  content is read only when you tap *Paste response*, never on resume. See
  [WORKOUT_JSON.md](WORKOUT_JSON.md).
- **Translations never touch the runtime.** [Crowdin](https://crowdin.com/project/pulse-workout) is
  build-time tooling; the installed app contains no Crowdin SDK and makes no Crowdin calls.

## Health data

Health Connect is an on-device API. Other apps separately authorized there may read records PULSE
writes and handle them under their own policies:

- The Health Connect write ("share finished workouts") is a per-app permission you grant and can
  switch off under *Settings → Health* at any time.
- What is written: exercise sessions with per-interval segments. PULSE does **not** write heart-rate
  records.
- PULSE requests only the exercise-session write permission; it does not request health read access.
- See [SECURITY.md](../SECURITY.md#scope) for what is in scope for a vulnerability report.

## Export and import

- **Workout exchange** for generated or hand-written workouts is documented in
  [WORKOUT_JSON.md](WORKOUT_JSON.md). It is not the internal `WorkoutPlan` JSON.
- **Missing media** — an image or audio file that was deleted, or whose permission was revoked — is
  reported when the workout is opened; the workout itself still loads.

There is currently no weekly PULSE backup worker or full app-data export/import screen. Older
documentation describing eight weekly backups or a `pulse.backup` exchange format did not match
the implementation.

## Retention and deletion

Phone data remains until deleted, app storage is cleared or the app is uninstalled. Delete individual
sessions or use Settings → Delete all sessions; delete workouts in the library. Voice & audio
removes recording files as well as their database entries. Removing an imported audio reference
does not delete the user's original file.

Clear watch History to delete local watch sessions. Clear storage/uninstall on each device to remove
all local data. Health Connect records, Android backups, already shared files and exported workouts
must be deleted separately at their destinations. Revoking access stops future access but does not
erase those copies. Privacy questions can be sent to `security@nexalab.fr`.

## Device backup and transfer

Android's backup includes the database and preferences. Cloud backup excludes recordings, `shared/`
and `backups/`; device-to-device transfer can include recordings. See `backup_rules.xml` (Android
8–11) and `data_extraction_rules.xml` (Android 12+). The watch disables Android backup. PULSE does
not add a separate encryption layer to its database or exported files; local protection depends on
Android app-private storage and device security. Protect exported copies at their destination.
