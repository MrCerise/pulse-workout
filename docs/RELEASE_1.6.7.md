# v1.6.7 release verification

Version **1.6.7**, version code **15**, minimum SDK 26. No database schema migration or permission changes.

## Changes

- Workout expansion preflights interval count and repeat work before allocating the timeline; duration accumulation detects overflow.
- Backup imports are bounded to 32 MiB and use the same immutable data for preview and import. Existing records are protected from imported ID collisions.
- Draft filenames are derived from workout IDs, and legacy filenames are accepted only when they are safe path components.
- External workout-start links require in-app confirmation. Replacing a running workout requires an explicit choice and records the old session as stopped.
- Repository validation scripts use bounded, symlink-resistant source reads and atomic report writes; Kotlin cross-reference scans resist adversarial input.

## Automated checks

Local checks:

- `python3 scripts/check_ui.py`: resource references, XML/vector validity and English/French parity.
- `python3 scripts/check_brand.py`: all shipped vectors match the supplied SVG.
- `python3 scripts/contrast_check.py`: 125 measured palette pairs; no generated documentation diff.
- `python3 scripts/kotlin_xref_gate.py` and `python3 scripts/kotlin_parse_gate.py` (the latter requires tree-sitter packages).
- Python compilation, adversarial regex/symlink checks and `git diff --check`.

GitHub Actions also runs `:engine:test :app:testDebugUnitTest` on the pull request. The release workflow builds signed release and debug APKs when tag `v1.6.7` is pushed.

## Device checks still required

The development environment has no Android SDK or emulator; these checks are not completed:

1. Upgrade a signed 1.6.6 install and verify workouts, sessions, settings and backups are preserved.
2. Import backups below and above the size limit; verify the previewed snapshot is what gets imported and collisions preserve existing records.
3. Open a `pulse://start/<id>` link: the workout must not start before confirmation. Dismissing must leave an active session running; confirmed replacement must record it as stopped.
4. Verify oversized/deeply repeated workouts fail promptly and ordinary plans still start.
5. Install the signed 1.6.7 release over 1.6.6 to confirm signing-key continuity.

---

See also: [CHANGELOG.md](../CHANGELOG.md#167--2026-10-14) · [VERIFICATION.md](VERIFICATION.md) · [RELEASING.md](RELEASING.md)
