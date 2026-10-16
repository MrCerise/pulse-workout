# Release 1.8.0

The first release as **Intervals**, on the Momentum design system.

## Verification

- `python3 scripts/check_ui.py` — resources resolve, vectors parse, en/fr parity, Kotlin balance.
- `python3 scripts/check_brand.py` — the seven brand vectors match `docs/brand/pulse.svg`; the
  launcher layers centre the 50 dp mark in the 108 dp viewport.
- `python3 scripts/contrast_check.py` — 125/125 measured pairs ≥ WCAG 2.1 AA; XML ↔ Compose ↔ Wear
  tokens byte-identical (`docs/CONTRAST.md` regenerated).
- `python3 scripts/kotlin_parse_gate.py` / `kotlin_xref_gate.py` — clean.
- Unit tests: engine, sync, library, app and wear modules.
- CI: pull-request workflow (gates + tests) must pass before merge; the `v1.8.0` tag push builds
  the signed release APKs and attaches them to the GitHub Release.

## Store distribution

- Big releases: push `v<semver>` (or run the release train with channel `production`) — the
  workflow computes `versionCode = major*10_000_000 + minor*100_000 + patch*1_000` so store
  uploads can never regress.
- Test/hotfix builds: run the release train with `internal` or `beta` — signed artifacts land on
  the workflow run, and the matching Play track when `ANDROID_SERVICE_ACCOUNT_JSON` is configured.
  Production users see nothing until a production run.
