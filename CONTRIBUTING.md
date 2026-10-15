# Contributing to PULSE

Thanks for wanting to help out. Bug reports, translations, documentation fixes, tests and code are
all welcome. This page covers how we work so your time isn't wasted.

If you have found a security problem, please skip this page and follow
[SECURITY.md](SECURITY.md) instead.

- [Ways to contribute](#ways-to-contribute)
- [Before you start](#before-you-start)
- [Development setup](#development-setup)
- [Running the checks](#running-the-checks)
- [Coding guidelines](#coding-guidelines)
- [Commits and pull requests](#commits-and-pull-requests)
- [Review expectations](#review-expectations)
- [Reporting bugs and suggesting features](#reporting-bugs-and-suggesting-features)
- [License](#license)

## Ways to contribute

| I want to… | Start here |
| --- | --- |
| Report a bug or request a feature | [Issue templates](https://github.com/MrCerise/pulse-workout/issues/new/choose) |
| Fix a typo or an obvious bug | Open a pull request — no issue needed |
| Translate the app | [Crowdin](https://crowdin.com/project/pulse-workout) and [docs/TRANSLATING.md](docs/TRANSLATING.md) |
| Change a screen or add one | Read [docs/DESIGN.md](docs/DESIGN.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) first |
| Change the timer, storage or the workout format | Open an issue first — see [before you start](#before-you-start) |

## Before you start

- **Small fixes** (typos, an obvious bug, a missing translation): just open a pull request.
- **Anything bigger** (a new feature, a new screen, a change to how the timer, storage or the
  workout format behaves): please open an issue first and say what you have in mind. A five-minute
  discussion up front can save you a weekend of work on something we can't merge.
- Look through the [open issues](https://github.com/MrCerise/pulse-workout/issues) first — someone
  may already be on it. Issues labelled [`good first issue`](https://github.com/MrCerise/pulse-workout/labels/good%20first%20issue)
  are a good place to start. If you pick one up, leave a comment so others know.

### What PULSE won't do

PULSE is offline-first: no accounts, no ads, no analytics, no backend. We won't merge changes that
add tracking or crash-reporting SDKs, ad libraries, required sign-in, or network calls that send user
data anywhere without the user explicitly asking for it. If you aren't sure whether an idea fits, ask
in an issue.

## Development setup

You need:

- **JDK 17** (the one bundled with Android Studio works)
- the **Android SDK** with **platform 37** and **build-tools 37.0.0** (36 also works)
- **Python 3** for the resource and contrast gates

Android Studio is optional; everything below works from a terminal.

```bash
# Point the build at your SDK (gitignored)
echo "sdk.dir=/absolute/path/to/Android/sdk" > local.properties

# Build and install a debug APK
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug build uses the package id `com.pulse.intervalcoach.debug`, so it installs alongside a
release build without touching your real data.

If Gradle runs out of memory on a smaller machine, see
[Quick start](README.md#quick-start) in the README for the low-memory settings.

## Running the checks

CI runs these on every pull request. Running them locally first saves a round trip:

```bash
# Unit tests (no device needed) — this is what PR CI runs
./gradlew :engine:test :sync:test :library:test :app:testDebugUnitTest :wear:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# Resource, brand, contrast and reference gates (no Android SDK needed)
python3 scripts/check_ui.py
python3 scripts/check_brand.py
python3 scripts/kotlin_xref_gate.py
python3 scripts/contrast_check.py
```

| Gate | What it fails on |
| --- | --- |
| `contrast_check.py` | Token drift between `colors.xml` and `Palette.kt` (exit 2), or any contrast pairing below its WCAG threshold (exit 1). It **rewrites `docs/CONTRAST.md`** — if you touched colours and the file changed, commit it, because CI fails on a mismatch. |
| `check_ui.py` | An unresolvable resource, a malformed vector, or an English string with no French counterpart. |
| `check_brand.py` | A brand asset that does not match the expected mark. |
| `kotlin_xref_gate.py` | A broken import, container reference, preference field or component call site. |
| `kotlin_parse_gate.py` | Syntax errors. Optional, and needs `pip install tree-sitter tree-sitter-kotlin`. |

More detail on what is and isn't proven by these gates: [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Coding guidelines

Follow the style of the code around your change. These are the things that matter specifically in
this project:

**The timing engine.** `engine/` is plain Kotlin with no Android dependencies and gets its clock
through the constructor. Please keep it that way. Any change to timing behaviour needs a test in
`engine/src/test` — that is where pauses, clock jumps and the Tabata edge cases are covered.

**Strings and translations.** Translate on [Crowdin](https://crowdin.com/project/pulse-workout); see
[docs/TRANSLATING.md](docs/TRANSLATING.md). Never hardcode user-visible text. Add every new string to
both `app/src/main/res/values/strings.xml` and `values-fr/strings.xml`. If you don't speak French,
copy the English text into the French file and say so in your PR — someone will translate it.

**Colours and design.** Take colours, spacing and shapes from the theme tokens in `ui/theme/`, never
from hardcoded values. [docs/DESIGN.md](docs/DESIGN.md) describes the design system. If you add a new
colour pairing, add it to `scripts/contrast_check.py` too.

**Accessibility.** Touch targets of at least 48 dp, content descriptions on icon-only buttons, and
colour should never be the only way something is shown. Every drag interaction needs a non-drag
alternative. Testing with TalkBack before you submit helps a lot.

**Database.** If you change a Room entity, bump the schema version, write a migration, commit the
exported schema under `app/schemas/` and extend `DatabaseMigrationTest`. People's workout history
must survive an update.

**Workout JSON format.** Changes to the import format need matching updates to
[docs/WORKOUT_JSON.md](docs/WORKOUT_JSON.md), `docs/workout-import.schema.json` and, where it makes
sense, the example files. Files that import today should keep importing.

**Untrusted input.** Imports, backups, deep links and clipboard content are all attacker-controlled.
Keep reads bounded, validate before writing, and never resolve a caller-supplied path into app
storage.

**Dependencies.** Please don't add a new library without talking about it in an issue first. Every
dependency ends up in a health app's APK.

## Commits and pull requests

1. Fork the repo and create a branch from `main` (for example `fix/pause-after-lock`).
2. Keep each PR to one topic. Two unrelated fixes are easier to review as two PRs.
3. Write commit messages as a short summary in the imperative ("Fix pause after screen lock", not
   "fixed stuff"), with more detail in the body if it's needed.
4. **Don't bump `versionCode` / `versionName` and don't edit [CHANGELOG.md](CHANGELOG.md).** That
   happens at release time — see [docs/RELEASING.md](docs/RELEASING.md).
5. Fill in the pull request template. For UI changes, include before and after screenshots, ideally
   in both light and dark themes.
6. Link the issue your PR addresses (`Fixes #123`).

Draft PRs are fine if you want early feedback.

## Review expectations

A maintainer will look at your PR when they can. This is a spare-time project, so it may take a few
days. We may ask for changes, which is normal and not a judgment of your work. If a week goes by
without a response, feel free to leave a comment.

AI coding tools are fine to use. Just make sure you understand and have tested what you submit,
because you'll be the one answering questions in review.

## Reporting bugs and suggesting features

Use the [issue templates](https://github.com/MrCerise/pulse-workout/issues/new/choose). For bugs, the
most useful things are the PULSE version, your Android version and device, and exact steps to
reproduce. Timer and audio bugs often depend on the device (battery optimisation, TTS engine,
Bluetooth headphones), so those details matter.

Please leave personal health data out of public issues. If you attach a workout file or a backup,
make sure it contains nothing you wouldn't want public.

## License

PULSE is released under the [MIT License](LICENSE). By contributing, you agree that your
contributions are licensed under the same terms.
