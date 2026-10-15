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
| `versionCode` | `app/build.gradle.kts` | Monotonic integer, bumped on **every** release (currently 18 → 1.7.2) |

Both `app/build.gradle.kts` and `wear/build.gradle.kts` carry the same `versionCode`/`versionName`;
bump them together so a release tag always produces matching phone and watch APKs.

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

`.github/workflows/build-release.yml`:

| Job | Trigger | What it runs |
| --- | --- | --- |
| `resource-checks` | pull requests, merges to `main`, tags, manual runs | `check_ui.py`, `check_brand.py`, `kotlin_xref_gate.py`, `contrast_check.py`, then `git diff --exit-code -- docs/CONTRAST.md` |
| `unit-tests (engine, sync, library)` | pull requests, merges to `main`, manual runs | `:engine:test :sync:test :library:test` |
| `unit-tests (app)` | pull requests, merges to `main`, manual runs | `:app:testDebugUnitTest` |
| `unit-tests (wear)` | pull requests, merges to `main`, manual runs | `:wear:testDebugUnitTest` |
| `release` | tags `v*`, manual runs with `release_tag` | signed `:app:assembleDebug :app:assembleRelease :wear:assembleDebug :wear:assembleRelease`, four APKs attached to the GitHub release |

Every Gradle job reports its own compiler diagnostics and failing test names in the job output, and
keeps its build log as an artifact when it fails.

Why the jobs are arranged this way:

- **The unit tests are three jobs, not one.** A public repository gets as many runners as the run
  has jobs, so the wall-clock cost of a pull request is the slowest job instead of the sum of all of
  them, and a failure in one group cannot hide the results of the others.
- **Merges to `main` run the same checks as pull requests, and they are what keeps CI fast.**
  Gradle cache entries are branch-scoped, so the runs on `main` are the ones that produce a cache
  every other branch and every tag can restore. Pull requests opened from this repository write
  their own branch-scoped entries too, which is what makes a second push to the same pull request
  cheaper than the first; a pull request from a fork stays read-only, so untrusted code cannot plant
  a cache entry that a later trusted build would restore. Tag runs are read-only as well — an entry
  written for a tag is visible to that tag alone — but they still restore `main`'s.
- **Documentation-only changes skip CI.** A pull request or a merge whose files all match the
  workflow's `paths-ignore` list runs nothing: prose, templates and the licence cannot change what
  compiles. Path filters are not evaluated for tag pushes, so a release is never skipped this way.
- **The Android SDK step probes the runner first.** The runner image already ships the platforms and
  build tools, so the step only calls `sdkmanager` when nothing usable is on disk.

Pull-request runs are cancelled when a new push supersedes them; merges to `main`, tag runs and
manual release runs never are — the runs on `main` are what write the caches, and a cancelled
release publishes nothing.

## Publishing

```bash
git tag -a v1.7.1 -m "v1.7.1"
git push origin v1.7.1
```

The workflow builds the phone and watch APKs, names them `pulse-<tag>-debug.apk`,
`pulse-<tag>-release.apk`, `pulse-<tag>-wear-debug.apk` and `pulse-<tag>-wear-release.apk`,
generates release notes and attaches them to the release.

## Re-signing an existing release

If a release was published before signing was configured, run **Actions → Build and release → Run
workflow** on `main` with `release_tag` set to the existing tag (for example `v1.7.0`). The workflow
builds, signs and re-uploads the APKs with `overwrite_files: true`.
