# Changelog

All notable changes to Interval Coach are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html) for the *version name*
(`versionName`); the Android `versionCode` is incremented with every release.

- Every release is tagged `v<version>` and published with signed APKs on the
  [releases page](https://github.com/MrCerise/pulse-workout/releases).
- Per-release verification notes live next to this file as `docs/RELEASE_<version>.md`.
- Version bumps and release notes are made by maintainers at release time, not in feature PRs (see
  [CONTRIBUTING.md](CONTRIBUTING.md)).

## [1.8.6] — 2026-10-19

**Fixed**
- Voice cues no longer stay muted for the rest of a workout after a permanent audio-focus loss,
  and the mute button always re-enables them.
- The phone timer applies the countdown, halfway and halfway-minimum preferences.
- Previous interval restarts the earlier step instead of immediately skipping forward again.
- Group ladders lengthen matching intervals per round; the Mobility Ladder tag shows 5 minutes.
- Completed sessions are always finalised once, the heartbeat stores elapsed time, and route data
  is captured before tracking stops.
- Watch session logs are sent as soon as they finish, and short watch sessions are acknowledged.
- Headphone and missed-interval cue messages follow the app language.

**Changed**
- Refreshed home screen quick-start layout.


**Changed**
- Version-tag uploads now target closed testing track `PreRelease Beta` as a draft for review.
- Both phone and Wear OS report version 1.8.5; tag builds use an increasing Play version code.

**Added**
- Versioned GitHub changelogs and localized Google Play release notes in English, French, German,
  Spanish and Italian, shared between publishing jobs without rebuilding the app.
- Validation for missing, empty or oversized release notes before release builds start.

The app includes the translations and fixes shipped in 1.8.4. This release updates distribution
and version metadata; it does not add new workout behaviour.

## [1.8.1-beta1.8] — 2026-10-17

**Fixed**
- Drop-down sheets are now real modal bottom sheets (platform window and insets), so the last
  option is always fully visible — including **AI workout** in the New workout sheet — with
  clear space under it above the gesture bar.

**Changed**
- Drop-downs/selectors and sliders (Voice verbosity, Cue volume, Speaking rate, Pitch and the
  Settings pickers) get a little more spacing between stacked controls, matching the
  background-audio section's volume + drop-down rhythm.

## [1.8.1-beta1.7] — 2026-10-17

**Changed**
- Library layout: the title band is clear; sort (labelled) and the list/grid toggle live in a
  toolbar under the search field with Favourites, and creating a workout is the floating + button.
- Duration entry is a number scroll: tap the value to open hour / minute / second wheels and
  scroll to the time — the ± buttons and type-in fields are gone.
- Player controls are one centred, modern transport cluster: round previous/next buttons around
  an 88 dp play/pause disc, with +15 s and Lap as centred pill buttons underneath.
- Uniform spacing in Settings → During workouts, with a larger gap before Voice & audio.

**Fixed**
- Bottom sheets respect the navigation-bar inset, so the last option (AI workout in the
  New workout sheet) is always visible.
- Image sharing grants read access to chooser targets explicitly and falls back to a text
  share if the image cannot be rendered, so sharing always works.

## [1.8.1-beta1.6] — 2026-10-16

**Changed**
- Page headers can no longer collide with their actions: the centred title gets the band to
  itself, back sits on its own left-aligned row, and header actions sit on a row under the title.
- Language and workout Category now use the same drop-down (bottom-sheet) style as every other picker.
- The app package is now `com.mrcerise.intervalcoach` and the installed app name is **Interval Coach**.
- Duration steppers move in ±5 s and ±1 min steps, and tapping the value types an exact min:sec.
- Workout, interval and block names are all big inline-editable boxes — no rename popup.
- Run / Walk is folded into **Running** (existing Run / Walk workouts show as Running).
- Distinct Today stats (active today, sessions today, next planned) instead of repeating Progress.

**Added**
- Running workouts record their GPS route; it is stored with the session and drawn on the
  session summary, the history session detail and the share image.
- History filtering by search, status and period.
- QR-code sharing for a workout, with the app logo in the centre of the code.

**Fixed**
- Spacing between the "When audio is interrupted" and headphone rows in Settings.
- Sessions under one minute are no longer stored or counted.

## [1.8.1-beta1.4] — 2026-10-16

The sheet-and-hero pass: pickers become modern bottom sheets, page titles are centred, and the
session screen is rebuilt around the countdown.

**Changed**
- **Drop-downs are gone; pickers are modern bottom sheets.** Every option choice (Phase, Sound,
  Vibration in the builders; audio interruption, headphone behaviour, density, units, verbosity in
  Settings) now opens a `PulseBottomSheet`: a rounded sheet that slides up from the bottom with
  large option rows and a check on the selected one. The pickers' rows are modern settings rows —
  label on the left, current value and a chevron on the right — and action menus (**Add to
  workout**, **Adjust all durations**, **Interval actions**, **More actions**, the Voice & audio
  tools, the library's sort / new-workout / row actions) open the same sheet, so the app has one
  modal surface instead of a web-form menu.
- **Page titles are centred.** `PulsePageHeader` centres its H1 on the page — the workout title at
  the top now sits in the same optical place as Progress and Settings — with the page actions
  floating at the end of the same band.
- **The session screen is rebuilt timer-first.** The countdown is the page: one dominant ring with
  the interval's own colour as a soft wash rising behind it, the timer scaled up (~74 sp on standard
  density), and the phase, round and side read inside the ring under the digits. The workout's name
  is centred at the top between the exit and mute controls. Everything else is micro or anchored:
  one centred line of session facts (elapsed · remaining · heart rate · music), the race band for
  running workouts, and the next interval, timeline and transport controls stacked at the bottom.

## [1.8.1-beta1.3] — 2026-10-16

The interface pass: imposing buttons, drop-downs where a menu is the honest control, and a share
image that can no longer draw text on top of text.

**Changed**
- **Buttons are modern and imposing.** Action buttons grow to 56 dp and speak in the display face
  (Space Grotesk semibold) instead of 14 sp body text; secondary buttons sit on the raised surface
  so they read as controls rather than as outlines. The stretched rows of half-width side-by-side
  buttons are gone.
- **Drop-downs replace button rows where a menu is the honest control.** Every enumerated choice —
  Phase, Sound and Vibration in the interval editor, and audio interruption, headphone behaviour,
  player density, units and verbosity in Settings — is now a labelled drop-down (`PulseSelect`)
  whose trigger shows the current value. Clusters of secondary actions collapse into one
  `PulseActionMenu`: **Add to workout** (interval / block), **Adjust all durations** (±5 / ±10 s),
  **Interval actions** (left+right pair, delete), **More actions** on the workout page (preview
  cues, edit, schedule), and the Voice & audio tools (voice actions, sound-cue preview, music).
- **The quick builder's workout name is an imposing name plate**, not a small stretched field: one
  64 dp button-styled plate with the name in 21 sp display type, centred, opening a rename dialog
  on tap. The advanced builder uses the same plate.
- **The advanced builder's interval editor drops open from the whole bar.** Clicking anywhere on an
  interval or block row — not just its name — expands the editor; the row is the tap target.
- The language note under Settings → Language ("Applied immediately. Widgets and notifications
  follow the same choice on Android 13 and newer.") is removed.

**Fixed**
- The share image no longer draws text over text: the wordmark and its tagline are measured and
  spaced from each other, the status line sits under the *rendered* title (so a two-line name
  pushes it down instead of overrunning it), and the completion percentage and date are pinned to
  the right margin instead of to guessed x-coordinates.

## [1.8.1-beta1.2] — 2026-10-16

The running system, released as a pre-release for testing.

**Added**
- **Race info for running workouts.** New **Running** category (and Run / Walk) runs the running
  system: during the session the player shows live **speed**, **distance**, and — when the workout
  has a race distance — **what is left of the race**, plus the projected finish.
- **GPS when you grant it, honest numbers when you don't.** Location is requested only when a
  running workout starts. With permission, speed and distance are measured from real fixes (glitchy
  fixes and jumps are filtered); without it, or indoors, numbers are projected from the plan's
  target pace and labelled *Estimated* — never presented as measurements. Pauses stop both the
  distance and the moving clock.
- **Race setup in the builder** (Race section): target pace per kilometre and race distance in
  kilometres (0 = open-ended run). The session summary reports distance, average speed and the
  source (GPS / Estimated).
- **5K Race Run** starter template, wired for race info out of the box.
- The exchange format's `type` accepts `RUN` (schema and docs updated).

**Changed**
- Every option picker — Phase, Sound and Vibration in the interval editor, and audio interruption,
  headphone behaviour, player density, units and appearance in Settings — is now a wrapping row of
  **standalone chips** (`PulseChipGroup`): each option is its own control with its own surface and
  touch target, spaced apart. The segmented bar of welded-together buttons inside a track
  (`PulseSegmented`) is gone; its cramped, truncated labels were the bug.
- Rows of icon buttons (builder interval rows, the player top bar, library rows) now carry explicit
  spacing, so no two touch targets touch.

## [1.8.1-beta1.1] — 2026-10-16

The container-within-container pass, released as a pre-release for testing.

**Changed**
- Settings and Voice & audio: every section now sits directly on the canvas — the segmented
  controls, pickers, steppers and buttons keep their own surfaces, and the cards that wrapped
  them are gone.
- The workout details and category heroes are flat: icon tile, hue-tinted title, duration and
  stat tiles on the canvas, with no card around them.
- Favorites and templates in the Today rails: the Start/Use action is a control in the card's
  own row (the template card itself is tappable) instead of a button block inside the card.
- The builder pages are flat forms with section headers; the interval list is a hairline-
  separated table rather than a stack of node cards.
- Health, JSON import and Goals pages: same treatment — controls on the canvas, one surface
  where one is needed. The Planner's schedule button sits under its list.
- Design principle codified in `docs/DESIGN.md` (§1.5 "One surface, not two").

## [1.8.1-beta1.0] — 2026-10-16

Interface polish pass, released as a pre-release for testing.

**Changed**
- One button system everywhere: the section-header text links on Today ("All workouts",
  "History"), the settings "Voice & audio" text link and every remaining `TextButton` are now
  full-width outlined buttons in the same style as the Help and Welcome-tour buttons.
- Option groups ("When audio is interrupted", headphone behaviour, player density, units, the
  builder pickers) are a single modern segmented control instead of a two-column chip grid.
- Today: Quick start and the weekly chart are flat sections (no container inside a container),
  the health snapshot is two tiles on the canvas, and the page gutter widens to 20 dp.
- The category page lists its workouts as one 56 dp-row table with proper insets.
- The session screen is rebuilt: labelled elapsed/remaining stats, a centred phase band, a flat
  "next up" line instead of a stray bordered panel, and a single padding system.
- The share image is redrawn on the app's own palette (ink panel, volt accent, raised tiles).
- The welcome tour gains a fourth step — the weekly goal — with preset chips and a slider; the
  goal is a real preference used by the hero, Today's ring and the Goals page, where it can be
  changed any time.
- Language moves out of Appearance into its own settings section.
- Scheduling is a proper page: the app's own month calendar, hour/minute steppers, a searchable
  workout list and one primary action (replaces the "Tomorrow · workout" links and the
  confirm-dialog flow). Also reachable from any workout's details page.
- Builder error and discard dialogs use the app dialog; release tags may now carry a pre-release
  suffix (`v1.8.1-beta1.0`), which marks the GitHub release as a pre-release.

## [1.8.0] — 2026-10-16

The product release: the interface settles on the **Momentum** look under its final name,
**Intervals**, and the feature set is trimmed to what a phone-only, offline-first timer needs.

**Changed**

- **The name is now Intervals** — it is a HIIT & Tabata interval timer and the onboarding says so.
  The welcome subtitle reads "The HIIT & Tabata interval timer" in every packaged language.
- **The supplied four-bar mark is back everywhere** — launcher, notification, splash, sidebar and
  the share card, phone and watch. It loads instantly in every launcher; the brand source stays
  `docs/brand/pulse.svg` with the original geometry gate.
- **Light theme, refreshed.** The volt accent deepens to a clean lime (`#4D7C0F`) for links,
  selection and focus, and its container tint is a true pale lime (`#ECFCCB`) instead of cream.
  All 125 contrast pairs re-measured and passing.
- **Today is one dashboard.** Progress and Goals are two proper nav tiles instead of orphan text
  links; the quick builder's actions are a balanced Start/Customize pair instead of three stacked
  full-width buttons.
- **Starter templates work like favourites** — an inline rail on Today with a one-tap *Use* that
  saves a copy and opens it. The separate templates page is gone, along with its route, its chip in
  the library and its sidebar entry.
- **The theme sun/moon switch is gone** from the shell breadcrumb and the tablet rail: appearance
  changes in Settings → Appearance, where the previews show what will change. Nothing mid-session
  can flip the palette any more.
- **Settings is a settings page.** The Data section (SAF export/import, automatic backups and the
  backup notification channel) is removed — data lives on the phone and is never transferred —
  leaving a single Danger-zone action for deleting session history. The duplicate AI/import entry
  is gone (the workouts page owns import), and **Help & about** no longer carries the version: the
  version and build number now sit at the bottom-right of Settings in the quietest ink.

**Removed**

- **Google Fit.** Deprecated by Google and legacy here; Health Connect is the one backend. The
  Play-services fitness dependency, its manifest query, its authorisation flow and its settings
  copy are gone.
- **Backups & restore.** See above; `BackupRepository`, the weekly `BackupWorker` and their tests
  are removed with the UI.

**Fixed**

- **Heart rate loads.** The dashboard reads today's latest measurement (it used to find nothing
  outside a five-minute window), the player reads a strict live window so a stale beat can never
  pose as current, and the Today screen refreshes the health snapshot when it appears.

**Added**

- **Release train** (`.github/workflows/release-train.yml`): one dispatch for `internal`, `beta`
  and `production` — tests, signed AAB + APK builds, Play tracks via a service-account secret,
  staged rollout on production, and a GitHub Release with the APKs. `versionCode` now derives
  monotonically from the semver (`major*10M + minor*100k + patch*1k + build`), shared by the
  tagged-release workflow, so a store upload can never regress.

## [1.7.3] — 2026-10-15

It is called Pulse Workout, and it can pick its own language.

**Added**

- **A language the user chooses**, in Settings → Appearance → Language: follow the device, or pin
  English, French, German, Spanish or Italian. The choice is stored with the other preferences and
  applied to the composition, so it lands on the tap without recreating the Activity — no restart, no
  blank frame, nothing cancelled underneath the running session. On Android 13 and later the same choice
  is offered to the platform, so home-screen widgets, notifications and anything inflated outside
  Compose agree with the screen. The shell reads its labels from resources now too — the bottom bar,
  the sidebar's groups and the breadcrumb titles — because a bar that stays English under a French app
  makes the setting look broken. That handover is repeated at every app start — Android restores the
  app's preferences and the platform's own locale store separately, so a restored backup can otherwise
  leave them disagreeing — and a write the platform already agrees with is skipped. Only the app's
  language changes; the device's never does.
- A language is offered only if its tag names a `values-…` folder the build packages, which is now a
  test (`AppLanguageTest`) rather than a convention, together with the two other ways this could rot:
  a renamed enum entry silently resetting the stored choice, and a translation table carrying a key the
  English source never declared.
- `docs/RELEASE_1.7.3.md`.

**Changed**

- **The app is named Pulse Workout everywhere it says its name.** `app_name` is the one string, driving
  the launcher label, the welcome screen, the About card, the share image, both home-screen widgets, the
  notification channel and the manufacturer line Health Connect and Google Fit show; the four places that
  draw the wordmark — welcome, About, the share image's light and dark sheets, the in-content lockup —
  set the same two-word lockup, "Workout" in the brand colour at a lighter weight than "Pulse". The
  package, application id, resource names, artifact names, repository and Crowdin project keep theirs,
  so an installed phone updates in place and translation history survives. The watch stays wordless.
- **The launcher icon draws its mark smaller**: 50 dp inside the 108 dp adaptive viewport instead of
  60 dp, centred, on the foreground and on the monochrome layer Themed icons use, on phone and watch.
  `scripts/check_brand.py` derives the scale from the vectors and compares it with what `docs/DESIGN.md`
  states, so the artwork and the reference cannot drift apart unnoticed.
- `WatchSessionRecorder` now contains both of its fan-out calls itself. A sync link that throws used to
  be the caller's problem; it is the last thing a finishing session does, so the exception unwound into
  the engine's teardown. The local history still lands first, and the failure stays inside the recorder.
- The compile SDK is resolved from what the machine has instead of being hardcoded in both APK modules:
  an explicit `-Ppulse.compileSdk` is honoured exactly, otherwise API 37 when it is installed, otherwise
  the newest installed platform at or below it, with the substitution printed.

**Fixed**

- **`./gradlew assemble` no longer dies on a missing platform SDK.** Without the CI action's
  `-Ppulse.compileSdk`, AGP asked for `platforms;android-37`, did not find it, tried to download it, and
  failed the whole build where a platform could not be reached — which is exactly how the CodeQL
  `Analyze (java-kotlin)` job's autobuild step was failing. The fallback needs no network, no flag and
  no change to how anyone builds; `-Ppulse.compileSdk=37` still gets API 37 or the build fails loudly.
- Tests: `AppLanguageTest` (6 cases) and a fourth `WatchSessionRecorderTest` case covering a link
  that fails on both calls; 116 JVM test methods
  across the five modules.
- Docs: README (feature list, module map, a troubleshooting row for the platform-SDK failure),
  `DESIGN.md` §10 sizes, `ARCHITECTURE.md`'s string-resource rule and `locale/` package,
  `TRANSLATING.md` on what it takes for a language to be offered, refreshed counts in
  `ACCEPTANCE.md` and `VERIFICATION.md`, and the language-resolution note in `VERIFICATION.md`.

## [1.7.2] — 2026-10-15

**Added**

- **The watch app runs on its own.** Unpaired — or with the phone link switched off — the watch
  seeds the bundled workout catalogue into its own store and runs sessions with the same engine as
  the phone. No pairing step, no phone in the room, nothing to configure.
- **`:library`, a shared catalogue module.** The fifteen starter workouts move out of the phone app
  into a pure-Kotlin module both APKs depend on. The phone seeds them into Room, the watch seeds
  them into its DataStore, and the identical `builtin-…` ids mean a sync reconciles the two copies
  as the same workouts instead of duplicating them.
- **Watch history.** The watch keeps its own record of the sessions it ran (the last 20, newest
  first) and shows where each one lives — "On your phone" or "On this watch" — so a workout done
  away from the phone is still visible on the wrist. Clearing it is a two-tap action.
- **A switch for the phone link.** Watch Settings → *Sync with phone* turns the link off: no
  library, preference, live-session or command traffic in either direction. Finished sessions are
  still recorded here and stay queued, so turning the link back on delivers them.

**Changed**

- Undelivered watch sessions are retried on a timer as well as when a peer appears, so a session
  finished while the phone was off is delivered without waiting for another session.
- Tapping a workout on the watch while a phone session is running now asks once, then stops the
  phone's session before starting locally: two devices never coach the same workout at once.
- The watch summary distinguishes *saved to the phone*, *waiting for the phone* and *saved on this
  watch*, instead of promising a sync a switched-off link will not perform.
- Home and Settings share one status line — solo, sync off, phone connected, no phone nearby — so
  the two screens cannot disagree.
- PR CI runs `:library:test`, and `check_ui.py` / `kotlin_xref_gate.py` cover the new module.

**Compatibility**

- No payload changed shape, so `PROTOCOL_VERSION` stays **1**: a 1.7.1 phone keeps syncing with a
  1.7.2 watch, and the watch keeps its library and history across the update.

Details: [architecture — the Wear OS link](docs/ARCHITECTURE.md),
[v1.7.2 release verification](docs/RELEASE_1.7.2.md).

## [1.7.1] — 2026-10-15

**Added**

- **Wear OS companion app** (`pulse-<tag>-wear-release.apk`, same package name as the phone app).
  The watch runs workouts with the exact same engine as the phone — monotonic, drift-free timing
  — with haptic cues mapped from each interval's cue setting, and keeps timing alive across wrist
  turns under a partial wake lock.
- **Phone ↔ watch sync.** Workouts, favourites and the appearance/cue preferences are mirrored to
  the watch automatically; deletions and renames propagate through a manifest reconciliation. A
  finished watch session lands in the phone's history (and Health Connect, when enabled) with its
  full event trail.
- **Two-way session mirroring.** A session running on either device is mirrored live on the other
  at ~1 Hz, and pause / resume / skip / +15 s / stop work from either side. The phone shows a
  "workout running on your watch" card; the watch shows a "running on phone" entry.
- **Same theme on the wrist.** The watch ships the "Signal" dark, OLED and high-contrast palettes;
  `contrast_check.py` now fails the build if the mirrored values drift from the phone's, so the
  verified contrast ratios apply to both screens.

**Changed**

- Static gates cover the new modules: `check_ui.py` validates wear resources and EN/FR parity,
  `check_brand.py` verifies the watch launcher mark, `kotlin_xref_gate.py` resolves imports across
  all four modules, and PR CI runs `:sync:test` and `:wear:testDebugUnitTest` alongside the
  existing suites.
- Release builds produce two APKs per tag: the phone app and the watch app, both signed with the
  release key.

Details: [architecture — the Wear OS link](docs/ARCHITECTURE.md),
[v1.7.1 release verification](docs/RELEASE_1.7.1.md).

## [1.7.0] — 2026-10-14

**Added**

- Community translations. PULSE is now localized on
  [Crowdin](https://crowdin.com/project/pulse-workout); English sources synchronize automatically
  and approved translations arrive as reviewed pull requests.
- APKs include every contributed locale, with English fallback for missing strings.

**Changed**

- No runtime translation service, account requirement or new app permission is involved — Crowdin
  is build-time tooling only.

Details: [translation instructions](docs/TRANSLATING.md),
[v1.7.0 release verification](docs/RELEASE_1.7.0.md).

## [1.6.7] — 2026-10-14

**Fixed**

- Bounded backup imports; preview and import now use the same snapshot, so imported ID collisions
  can no longer overwrite existing records.
- Hardened nested workout expansion, draft storage paths and the external workout-start
  confirmation.
- Repository validation reads and contrast-report writes are bounded and symlink-resistant.

Details: [v1.6.7 release verification](docs/RELEASE_1.6.7.md).

## [1.6.6] — 2026-10-14

**Changed**

- New monochrome logo across the launcher, navigation, splash, notifications and shared summaries.
- Removed redundant hints, promotional copy, category blurbs and workout-description fields.
- Visual theme previews, softer controls and short colour/selection transitions. Reduced motion is
  respected and the shell theme toggle now follows the actual system appearance.

**Removed**

- The old text-description parser. Existing workouts, history and backups remain compatible.

**Added**

- AI workouts: share a prompt, paste a response for automatic preview, then save. Saving a single
  workout opens it directly; a batch opens the library.

Details: [release verification](docs/RELEASE_1.6.6.md), [AI workflow](docs/WORKOUT_JSON.md).

## [1.6.5] — 2026-10-14

**Fixed**

- **Volume buttons adjust media volume, always.** The workout-navigation shortcut was removed,
  including previously saved opt-ins.
- **Reliable cue ducking.** Player access is confined to the main thread, startup fades respect
  cues, queued speech is tracked by utterance ID, and a late callback can no longer end a newer
  duck. Longer tones are no longer clipped by a 125 ms PCM buffer.
- **Safer sharing.** Background rendering/encoding, duplicate-tap protection, recoverable errors,
  URI read grants, bitmap cleanup and expiry of old exported images. `FileProvider` no longer
  exposes the app's private files directory.

**Changed**

- Android audio focus is requested transiently around cues only, instead of for a whole workout.
- Redesigned 1080 × 1350 share card: Inter typography, orange accents, a completion bar, readable
  metric tiles, wrapped titles and the recorded session date. No private notes or health data in
  the image.

Details: [v1.6.5 release verification](docs/RELEASE_1.6.5.md).

## [1.6.4] — 2026-10-14

**Added**

- JSON workout import from a pasted response or a UTF-8 file, from *Workouts → +* or
  *Settings → Create workouts*.
- Versioned `pulse.workouts` format supporting batches, nested repeats, timed/manual intervals,
  tags, equipment and spoken coaching notes.
- **Bring your own AI.** Customize and copy a schema-aware prompt for ChatGPT, Claude, Gemini or
  another tool, then import its response. No API keys, backend or automatic data sharing.
- **Validate before saving.** Preview every workout and its expanded timeline, then save the whole
  batch in one transaction with fresh identities. Invalid input never modifies your library.
- English and French import UI, plus parser and import-state regression tests.

Details: [JSON format and AI workflow](docs/WORKOUT_JSON.md),
[JSON Schema](docs/workout-import.schema.json), [example](docs/examples/beginner-workout.json).

## [1.6.3] — 2026-10-11

**Fixed**

- **Editing a workout now edits it.** The builder destination carries the workout id
  (`builder/advanced?workoutId=…`), the details screen and the library row menu both pass it, and a
  save after an edit updates the existing row in place — same id, creation date and tags — instead
  of creating a second copy.
- **An edit cannot lose what it did not touch.** `WorkoutRepository.save` no longer clears tags when
  the caller has none to report, the row revision is mirrored inside the stored plan, undo/redo
  snapshots hold the whole plan, and the builder no longer stamps `createdAt` with "now" on every
  save.

**Changed**

- **Filter and option chips are rectangles.** Anything you tap is a control, and controls are 6 dp
  rounded rectangles; labels that carry no action keep the full radius. Material's `extraSmall`
  shape no longer maps onto the pill token.
- **Builder options are two per row**, so a "Cool-down" or "Double beep" label fits on a phone.

## [1.6.2] — 2026-10-11

**Fixed**

- **The version metadata now matches the release.** v1.6.1 was tagged without a
  `versionCode`/`versionName` bump, so its APKs still reported 1.6.0 (versionCode 9): phones and
  update checkers treated it as the build they already had. Same code as v1.6.1, correctly stamped.

## [1.6.1] — 2026-10-11

**Added**

- A **Goals** screen reachable from Home: workouts completed, total active minutes, current and
  longest daily streaks, and progress against a 1000-minute monthly target. A session that is still
  running does not count toward any of it.

**Changed**

- Clearer wording for the text parser: "Workout description" with a "Workout name" field,
  "Add workout description" in Settings, "Use text description" in the library menu.
- CI runs faster and skips work nobody uses.

**Fixed**

- Compile errors that blocked the Goals screen.

## [1.6.0] — 2026-10-11

**Changed**

- **"Signal": a new design system**, aimed at the Cloudflare dashboard rather than at a generic app:
  a warm charcoal ramp in the dark theme, clean paper in the light theme, **one** accent
  (`#F38020`, deepened to `#B23A0B` for text on paper), 6/8/12 dp radii, hairlines instead of
  shadows, and Inter set in a compact dashboard scale. Gradients are gone from the component layer.
- **A real application shell** — a 244 dp sidebar at ≥ 1000 dp, a 72 dp icon rail at ≥ 600 dp, a
  bottom bar on a phone, and one 56 dp breadcrumb bar above them.
- **Page headers instead of title bars** (`PulsePageHeader`), with back navigation deliberately in
  the page so an unsaved builder can intercept it.
- **Dense lists, not card soup**: the library is one hairline-separated surface, the summary's stat
  tiles became one metric table.
- **Typography is real Inter**, four static weights subset to Latin, with tabular figures so a
  countdown never shifts a pixel.

**Added**

- Accessibility enforcement now measures **125 pairs** across the ink, void and paper themes.

Nothing about the workouts changed: same routes, same engine, same data, same preferences.

Details: [docs/DESIGN.md](docs/DESIGN.md).

## [1.5.0] — 2026-10-11

**Changed**

- **"Prism": a rebuilt design system** — violet-tinted ink canvas, large soft surfaces, a 64 sp hero
  type scale and a two-stop accent gradient used in exactly three places.
- **Categories are data now.** One `WorkoutCategory` record per category in `data/Categories.kt`
  replaces both the `WorkoutType` enum and the free-text `iconKey`; the registry is the single
  source for every rail, chip, card, picker and breakdown.
- **Adaptive navigation**: a floating pill bar on a phone, a navigation rail on tablets.
- **Rebuilt screens**: Today, Workouts, details, gallery, Progress, player, onboarding, summary.
- **One screen frame** — every destination is a `PulseScaffold`.

**Added**

- A **category screen** for every category (`category/{key}`), generated from its registry record.
- Contrast enforcement now measures **97 pairs**.

## [1.4.4] — 2026-10-08

**Added**

- **Audio ducking that actually runs**: a 140 ms drop when the coach starts talking, a hold that
  covers the end of the sentence and a 420 ms climb back — a pure state machine
  (`audio/Ducking.kt`) covered by `DuckingTest`.
- **Switch and depth, applied live**: Voice Studio pushes the on/off switch and the depth slider
  into the running session as you move them.
- **Background audio plays for real**: fade-in over 320 ms, pause/resume with the session, preview
  from Voice Studio.
- **Volume keys can drive the workout** when the preference is on (up = next interval, down =
  pause/resume; auto-repeat ignored).

**Fixed**

- Ducking follows the speech engine through TTS progress callbacks, with a watchdog for engines
  that never report completion.
- A "may duck" interruption lowers the music and keeps coaching instead of muting it.

## [1.4.3] — 2026-10-08

**Fixed**

- Overlapping content in the Today weekly-activity and quick-start cards; their children now
  arrange vertically.

**Changed**

- Unit tests run on pull requests only; APK builds stay on pushes and releases.

## [1.4.2] — 2026-10-08

**Changed**

- **"Graphite":** the visual layer was rebuilt, not reskinned, and centralized — `ui/theme/` holds
  the palette, type scale and spacing/radius/motion tokens, and `ui/components/` is the only place a
  style is written. The design reference is Linear: near-black chrome, hairline borders, one
  restrained accent, tabular numerals.
- **Chrome is monochrome, colour means something** — gradients, glowing borders, phase washes and
  decorative glows are gone.
- **A new interaction layer** — navigation, buttons, fields, segmented controls, chips, badges,
  banners, dialogs, snackbars and loading/empty/error states, all driven by tokens.
- **Rebuilt screens** with consistent 16 dp gutters and one 56 dp header.

**Added**

- Contrast enforcement: `scripts/contrast_check.py` regenerates `docs/CONTRAST.md` and fails on
  token drift or any pairing below its threshold (81 pairs).
- `scripts/kotlin_xref_gate.py`, a stdlib-only static check over imports, container references and
  component call sites.

## [1.3.0] — 2026-10-07

**Added**

- **Health Connect is the primary health backend**: finished workouts written as workout sessions
  with per-interval segments (idempotent per session), live heart rate in the player, today's steps
  on the Today screen, and a dedicated Health setup screen with real platform state and 7-day
  backfill.
- **Google Fit as a secondary, best-effort backend.** Google deprecated the on-device Fit API, so
  the app ships without a Fit OAuth client id and says so in the UI instead of pretending.

**Changed**

- **Android 17 (API 37)** compile and target SDK.
- The "not implemented" health card is gone — the UI shows what the platform actually reports.

## [1.2.0] — 2026-10-07

**Changed**

- UI and layout overhaul across the app, plus a redesigned app icon.

## [1.0.1] — 2026-10-07

**Fixed**

- Follow-up maintenance release after the first publication; no user-facing change was recorded.

## [1.0.0] — 2026-10-07

**Added**

- First public release: the timing engine, the quick and advanced builders, the player, history,
  the template gallery and the starter workouts.

[Unreleased]: https://github.com/MrCerise/pulse-workout/compare/v1.7.0...HEAD
[1.7.0]: https://github.com/MrCerise/pulse-workout/compare/v1.6.7...v1.7.0
[1.6.7]: https://github.com/MrCerise/pulse-workout/compare/v1.6.6...v1.6.7
[1.6.6]: https://github.com/MrCerise/pulse-workout/compare/v1.6.5...v1.6.6
[1.6.5]: https://github.com/MrCerise/pulse-workout/compare/v1.6.4...v1.6.5
[1.6.4]: https://github.com/MrCerise/pulse-workout/compare/v1.6.3...v1.6.4
[1.6.3]: https://github.com/MrCerise/pulse-workout/compare/v1.6.2...v1.6.3
[1.6.2]: https://github.com/MrCerise/pulse-workout/compare/v1.6.1...v1.6.2
[1.6.1]: https://github.com/MrCerise/pulse-workout/compare/v1.6.0...v1.6.1
[1.6.0]: https://github.com/MrCerise/pulse-workout/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/MrCerise/pulse-workout/compare/v1.4.4...v1.5.0
[1.4.4]: https://github.com/MrCerise/pulse-workout/compare/v1.4.3...v1.4.4
[1.4.3]: https://github.com/MrCerise/pulse-workout/compare/v1.4.2...v1.4.3
[1.4.2]: https://github.com/MrCerise/pulse-workout/compare/v1.3.0...v1.4.2
[1.3.0]: https://github.com/MrCerise/pulse-workout/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/MrCerise/pulse-workout/compare/v1.0.1...v1.2.0
[1.0.1]: https://github.com/MrCerise/pulse-workout/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/MrCerise/pulse-workout/releases/tag/v1.0.0
