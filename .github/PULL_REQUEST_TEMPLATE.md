<!--
Thanks for opening a pull request. Please fill in the sections below and delete the ones that don't
apply. For anything small — a typo, an obvious bug, a missing translation — a short description is
enough; you don't need to fill in every box.

Security fixes: please don't describe the vulnerability here. See SECURITY.md.
-->

## What does this change?

<!-- One or two sentences a reviewer can read without any context. -->

Fixes #

## Why?

<!-- What problem does it solve? Skip this if the linked issue already explains it. -->

## How was it tested?

<!-- For example: which unit tests, a device or emulator (model + Android version), TalkBack,
     light/dark theme, a JSON import round trip. -->

## Screenshots

<!-- For UI changes: before and after, in both light and dark theme if you can. -->

## Checklist

- [ ] `./gradlew :engine:test :app:testDebugUnitTest` passes
- [ ] `python3 scripts/check_ui.py`, `check_brand.py`, `kotlin_xref_gate.py` and
      `contrast_check.py` pass, and any change to `docs/CONTRAST.md` is committed
- [ ] New user-visible text is in both `values/strings.xml` and `values-fr/strings.xml`
      (translations themselves are made on Crowdin — see docs/TRANSLATING.md)
- [ ] Docs are updated if behaviour, the JSON format or the database schema changed
- [ ] No new network calls, analytics or dependencies (or they were discussed in an issue first)
- [ ] No `versionCode` / `versionName` bump and no CHANGELOG edit (that happens at release time)
