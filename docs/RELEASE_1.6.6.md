# v1.6.6 release verification

Version **1.6.6**, version code **14**, minimum SDK 26. No database migration or permission changes.

## Changes

- Supplied four-bar logo in the adaptive/themed launcher, shell, welcome, splash, notifications and
  share image. The original geometry is preserved in `docs/brand/pulse.svg`.
- Removed redundant explanations, category blurbs, repeated labels and implementation details from
  Settings, Health, Home, Progress, builders, onboarding, templates and summaries. Kept actionable
  errors, permission information, session notes and interval coaching instructions.
- Removed the workout-description parser and workout description editing/display/search. Legacy
  description fields remain only for saved-data and v1 JSON compatibility; templates/prompts no
  longer generate them.
- Visual theme previews, 12/16/24 dp radii, 48 dp action buttons, short theme/selection/route
  transitions. Reduced motion is respected. System theme toggles correctly; dynamic accents get a
  matching tint and readable foreground, with contrast checks and high-contrast fallback.
- AI import: share/copy prompt, paste for automatic preview, save to open the result. File imports
  also preview automatically; manual JSON edits are debounced. No service calls or automatic
  clipboard reads. Saving is still explicit and transactional.

## Automated gates

Local static checks:

- `python3 scripts/check_ui.py`: XML/vector validity, resource references and EN/FR parity.
- `python3 scripts/check_brand.py`: all five vector assets match the supplied SVG; launcher inset
  and pre-Android-12 splash drawable format.
- `python3 scripts/contrast_check.py`: 125 measured palette pairs and resource/token parity.
- `python3 scripts/kotlin_xref_gate.py`: project imports, screen references and preference fields.
- `python3 scripts/kotlin_parse_gate.py`: Kotlin syntax (requires tree-sitter packages).
- `git diff --check`.

GitHub Actions gates:

- `:engine:test :app:testDebugUnitTest` on the PR, including auto-preview/debounce/stale-input,
  double-save, file/save retry, size limits, batch navigation IDs, legacy-description round trips,
  resolved system appearance, dynamic accent contrast and reduced-motion durations.
- Signed minified release APK and debug APK built for `v1.6.6` by the release workflow.

## Device checks still required

The Arena sandbox has no Android SDK/emulator. These are a manual checklist, not completed tests:

1. Upgrade a signed 1.6.5 installation. Workouts, favorites, history, settings and old backups survive;
   old descriptions are absent from the interface. Removed parser entry points are gone.
2. Check the logo with circle/squircle launcher masks, Android 13+ themed icons, old/new splash,
   phone navbar, tablet rail/sidebar and shared image.
3. Change all four themes in Settings/onboarding; restart and rotate. Check System on a dark device,
   wallpaper accents on Android 12+, high contrast and OLED. Turn Reduced motion on and verify no
   theme, selection, route or editor animation. Test the Android animator-duration setting too.
4. Check narrow phones, landscape, large fonts and TalkBack: all theme choices have radio selection
   state; the welcome footer and AI Save action do not cover content or the keyboard.
5. Share/copy a custom prompt into an AI app; paste its reply. Preview appears without Validate.
   Save opens the workout directly; a batch opens Workouts. Cancel the sharesheet/file picker.
6. Paste empty, malformed, fenced, non-ASCII and oversized JSON. Edit rapidly and clear input during
   validation. Invalid input cannot reuse an old preview. A failed save can be retried once.
7. Rotate during preview/save and double-tap Save: one batch, no duplicates. Inspect every interval
   and cue in long/manual workouts before saving. Clipboard is never inspected on resume.
8. Check English and French. Start a workout, lock the screen, hear cues, end it and share a summary.
9. Install the signed release over 1.6.5 to confirm signing-key continuity.
