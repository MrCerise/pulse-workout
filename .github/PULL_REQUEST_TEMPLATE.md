<!--
Thanks for the PR! Please fill in the sections below. Delete anything that doesn't apply.
Security fixes: please don't describe the vulnerability here. See SECURITY.md.
-->

## What does this change?



Fixes #

## Why?

<!-- What problem does it solve? Skip this if the linked issue already explains it. -->

## How did you test it?

<!-- For example: unit tests, a device or emulator (model + Android version), TalkBack, light/dark theme. -->

## Screenshots

<!-- For UI changes: before and after. Light and dark themes if you can. -->

## Checklist

- [ ] `./gradlew :engine:test :app:testDebugUnitTest` passes
- [ ] `python3 scripts/check_ui.py` and `python3 scripts/contrast_check.py` pass (and any change to `docs/CONTRAST.md` is committed)
- [ ] New user-visible text is in both `values/strings.xml` and `values-fr/strings.xml`
- [ ] Docs are updated if behaviour, the JSON format or the database schema changed
- [ ] No new network calls, analytics or dependencies (or they were discussed in an issue first)
