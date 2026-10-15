# Releasing

How a PULSE release is cut, signed and published. This page is for maintainers; contributors do not
need it, and pull requests should **not** bump versions or edit release notes (see
[CONTRIBUTING.md](../CONTRIBUTING.md)).

- [Versioning](#versioning)
- [Release checklist](#release-checklist)
- [Signing setup](#signing-setup)
- [What the workflow does](#what-the-workflow-does)
- [Publishing](#publishing)
- [Re-signing an existing release](#re-signing-an-existing-release)

## Versioning

| Field | Where | Rule |
| --- | --- | --- |
| `versionName` | `app/build.gradle.kts` | SemVer: `MAJOR.MINOR.PATCH` |
| `versionCode` | `app/build.gradle.kts` | Monotonic integer, bumped on **every** release (currently 16 → 1.7.0) |

Bump both in the release commit — a tag without the bump produces an APK that phones and update
checkers treat as the previous build. That is exactly what happened to v1.6.1 and what v1.6.2 fixed.

## Release checklist

1. Merge everything intended for the release into `main`.
2. Bump `versionCode` and `versionName` in `app/build.gradle.kts` on a dedicated release branch.
3. Update [CHANGELOG.md](../CHANGELOG.md): turn `Unreleased` into a versioned section with its date.
4. Add `docs/RELEASE_<version>.md` — the per-release verification note: what changed, which
   automated checks ran, and which device/maintainer checks remain outstanding. Existing notes
   (`RELEASE_1.6.5.md` … `RELEASE_1.7.0.md`) are the template.
5. If the database changed: bump the schema version, commit the exported schema under `app/schemas/`
   and extend `DatabaseMigrationTest`.
6. If the workout exchange format changed: update [WORKOUT_JSON.md](WORKOUT_JSON.md),
   `docs/workout-import.schema.json` and the example files. Files that import today must keep
   importing.
7. Regenerate and commit [CONTRAST.md](CONTRAST.md) if any colour token changed.
8. Open a PR, let CI go green, merge it.
9. Tag the merge commit `v<version>` and push the tag.

## Signing setup

Tagged builds are signed; a tag release **fails** if the signing key is missing, rather than
publishing an unsigned APK as a signed release. Ordinary pushes to `main` still build without signing
credentials.

Create a release keystore and keep it backed up somewhere private. **Never commit the keystore or its
passwords** (`*.jks`, `*.keystore` and `keystore.properties` are gitignored):

```bash
keytool -genkeypair -v -keystore pulse-release.jks \
  -alias pulse -keyalg RSA -keysize 4096 -validity 10000
```

In **Settings → Secrets and variables → Actions**, add these repository secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of `pulse-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `pulse` (or the alias you chose) |
| `ANDROID_KEY_PASSWORD` | Password for the key |

In PowerShell, generate the base64 value with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("pulse-release.jks"))
```

## What the workflow does

`.github/workflows/build-release.yml` has three jobs:

| Job | Trigger | What it runs |
| --- | --- | --- |
| `resource-checks` | pull requests, manual runs | `check_ui.py`, `check_brand.py`, `kotlin_xref_gate.py`, `contrast_check.py`, then `git diff --exit-code -- docs/CONTRAST.md` |
| `unit-tests` | pull requests, manual runs | `:engine:test :app:testDebugUnitTest`, with compiler diagnostics summarized in the job output |
| `release` | tags `v*`, manual runs with `release_tag` | signed `:app:assembleDebug :app:assembleRelease`, APKs attached to the GitHub release |

Pull-request runs are cancelled when a new push supersedes them; tag and manual release runs never
are.

## Publishing

```bash
git tag -a v1.7.1 -m "v1.7.1"
git push origin v1.7.1
```

The workflow builds both APKs, names them `pulse-<tag>-debug.apk` and `pulse-<tag>-release.apk`,
generates release notes and attaches them to the release.

## Re-signing an existing release

If a release was published before signing was configured, run **Actions → Build and release → Run
workflow** on `main` with `release_tag` set to the existing tag (for example `v1.7.0`). The workflow
builds, signs and re-uploads the APKs with `overwrite_files: true`.
