# Changelog

All notable changes to PULSE are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html) for the *version name*
(`versionName`); the Android `versionCode` is incremented with every release.

- Every release is tagged `v<version>` and published with signed APKs on the
  [releases page](https://github.com/MrCerise/pulse-workout/releases).
- Per-release verification notes live next to this file as `docs/RELEASE_<version>.md`.
- Version bumps and release notes are made by maintainers at release time, not in feature PRs (see
  [CONTRIBUTING.md](CONTRIBUTING.md)).

## [Unreleased]

Nothing yet. See the [open issues](https://github.com/MrCerise/pulse-workout/issues) for what is
being worked on.

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
