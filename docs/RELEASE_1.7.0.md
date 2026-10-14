# v1.7.0 release verification

Version **1.7.0**, version code **16**, minimum SDK 26. No database migration or new permissions.

## Changes

- Crowdin project and Android resource mapping with documented maintainer onboarding.
- Native Crowdin GitHub integration for source synchronization and approved translation PRs.
- Removal of the English/French packaging filter so future contributed locales ship in APKs.

## Automated checks

Run `python3 scripts/check_ui.py`, `python3 scripts/check_brand.py`,
`python3 scripts/kotlin_xref_gate.py` and `python3 scripts/contrast_check.py`.
PR CI runs `:engine:test :app:testDebugUnitTest`. Pushing `v1.7.0` builds signed release/debug APKs
and attaches them to the GitHub release.

## Maintainer/device checks

- Configure the Crowdin GitHub integration and import existing translations as described in [TRANSLATING.md](TRANSLATING.md).
- Review the first sync PR and confirm the integration triggers CI.
- Verify English/French UI, formatting placeholders and spoken coaching on a device.
- After adding another locale, check it appears in the APK and missing strings use English.
- Install the signed 1.7.0 APK over 1.6.7 to verify signing continuity and data preservation.

These external setup/device checks are not claimed as completed by the source changes alone.
