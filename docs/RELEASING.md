# Releasing

How a PULSE release is cut, signed and published. This page is for maintainers; contributors do not
need it, and pull requests should **not** bump versions or edit release notes (see
[CONTRIBUTING.md](../CONTRIBUTING.md)).

- [Versioning](#versioning)
- [Release checklist](#release-checklist)
- [Signing setup](#signing-setup)
- [Google Play releases](#google-play-releases)
- [What the workflow does](#what-the-workflow-does)
- [Publishing](#publishing)
- [Re-signing an existing release](#re-signing-an-existing-release)

## Versioning

| Field | Where | Rule |
| --- | --- | --- |
| `versionName` | `app/build.gradle.kts` | SemVer: `MAJOR.MINOR.PATCH` |
| `versionCode` | `app/build.gradle.kts` | Local fallback; CI overrides it for releases |

Both `app/build.gradle.kts` and `wear/build.gradle.kts` carry the same `versionCode`/`versionName`;
bump them together so a release tag always produces matching phone and watch APKs.

Keep both modules' defaults in sync. The Play release train overrides them with its validated
`version_name` and required `version_code` inputs. Choose a version code higher than **every**
previous Play upload, including drafts and all testing tracks. Codes must be integers between
1 and 2,100,000,000. The workflow validates the format and range; the maintainer must check Play's
existing codes before dispatching. A workflow's run number cannot ensure uniqueness across workflows.

The separate GitHub APK workflow derives codes from the tag's numeric version, with a run-number
suffix for prereleases. If users install both GitHub and Play builds, choose the Play code above
the GitHub APK code too. Testing train builds append `-internal.<code>` or `-beta.<code>` to the
display version; production uses the plain version name.

## Release checklist

1. Merge everything intended for the release into `main`.
2. Bump the version defaults in both `app/build.gradle.kts` and `wear/build.gradle.kts` on a
   dedicated release branch.
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
9. For Google Play, dispatch **Release train** as described below. For a GitHub-only APK release,
   tag the merge commit `v<version>` and push the tag. The production train creates its own GitHub
   release after a successful non-draft Play upload (or an explicit artifact-only run).

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

## Google Play releases

Before submission, complete [Google Play compliance preparation](PLAY_COMPLIANCE.md): publish the
generated public privacy policy, review Data safety/health/foreground-service declarations, and add
the supplied health disclaimer to store descriptions. The policy must match the offline app text.
`python scripts/generate_privacy_policy.py --check` is enforced in CI.

The phone targets API 36 and Wear API 35 independently of compile SDK discovery. The Play phone job
requires compile SDK 36+ and passes `-Ppulse.playRelease=true`, which rejects a lower platform.

Use [`.github/workflows/release-train.yml`](../.github/workflows/release-train.yml) from
**Actions → Release train → Run workflow**. It builds signed phone and Wear OS AABs/APKs,
keeps both R8 mappings, and uploads the **phone** bundle and its mapping to Google Play.
Tag pushes continue to publish GitHub APKs only.

### One-time setup

**Certificate compatibility:** the keystore below signs Play uploads; it is not necessarily the app
signing key Google uses on delivered APKs. GitHub APKs signed with an upload key can differ from Play
installs. Phone/watch Data Layer requires matching signatures; compare installed APK certificates
against Play Console's app signing certificate before mixing channels. A Google-generated app key
cannot simply be downloaded for local signing. See [signing checks](PLAY_COMPLIANCE.md#signing-phonewatch-compatibility-and-api-levels).

1. Create the Play Console application for `com.mrcerise.intervalcoach`, finish its required setup,
   enroll in Play App Signing, and upload the first signed phone AAB manually. Use the upload key
   registered with Play for the four signing secrets above; an existing app needs its existing key.
   To produce that first bundle, run the train with `publish_to_play` disabled and download its
   signed artifacts. For a draft app, keep `release_status` set to `draft` on API uploads until
   the app is ready for publication.
2. Enable the Google Play Android Developer API in a Google Cloud project, create a service account
   and JSON key, then invite its email in Play Console **Users and permissions** with access to
   this app and permission to release to the intended testing/production tracks. Google's
   [API setup guide](https://developers.google.com/android-publisher/getting_started) describes
   the Cloud and Play Console steps.
3. Add the complete JSON key contents as the `ANDROID_SERVICE_ACCOUNT_JSON` Actions secret.
   Store it at repository level or in the environments below. It is plain JSON, **not base64**.
   Keep signing secrets at repository level, since the build jobs run before the Play environment.
4. Create GitHub environments `play-testing` and `play-production`. Configure their allowed branches
   and, if desired, required reviewers. Testing channels use `play-testing`; production uses
   `play-production`. Ensure the selected testing track exists and testers are configured in Play.

### Dispatch inputs

| Input | Behavior |
| --- | --- |
| `channel` | `internal` (default), `beta` (closed testing), or `production` |
| `version_name` | `MAJOR.MINOR.PATCH`, without `v`; empty reads the default from `app/build.gradle.kts` |
| `version_code` | Required unique increasing integer; use a new code for each upload |
| `publish_to_play` | Enabled by default; disable for signed artifacts without a store upload |
| `release_status` | `draft` (default) to prepare a Play release, or `completed` to release to the track |
| `staged_rollout_percent` | Integer 1–100; values below 100 require production and `completed` |
| `run_tests` | Enabled by default; production always runs tests, even if this is disabled |

For example, after checking that `18001050` exceeds all previous uploads, use `internal`,
`version_name=1.8.1`, `version_code=18001050` and `release_status=completed` to distribute to testers.
For production, use a fresh code, choose `completed` and a rollout such as `10` to reach 10% of users.
The workflow translates that into `status=inProgress` and `userFraction=0.10`. At 100%, it sends
`status=completed` without a fraction, as required by the
[Play track API](https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks).

To promote the exact tested bundle, finish a draft, expand/halt a staged rollout, or retry a store
submission after an edit was committed, use Play Console. Re-running an upload with an already
used code is rejected by Play; a new workflow upload requires a new code.

All channels retain signed phone/watch artifacts and mappings for 30 days. An enabled Play upload
fails if credentials are missing or the API rejects it; the signed artifacts remain downloadable.
A production GitHub release waits for Play success, and is omitted for Play drafts. Artifact-only
production runs still create the GitHub release. Trains serialize across channels to avoid
concurrent edits to the same Play app. GitHub may replace a pending run with a newer dispatch, so
wait for a train to finish before dispatching the next one.

Wear OS bundles are provided for manual distribution. They currently share the phone version code
and are **not uploaded by this workflow**: Play Wear publishing needs distinct version codes and
form-factor tracks such as `wear:production` (see
[Wear OS packaging](https://developer.android.com/training/wearables/packaging)).

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
- **A build without that step still compiles.** The root `build.gradle.kts` resolves `compileSdk`
  against the platforms installed on the machine and hands the value to both APK modules, so a plain
  `./gradlew assemble` — a code scanner's autobuild, a contributor's first build — works on a runner
  that has no `platforms;android-37` instead of failing on the download attempt. CI's explicit
  `-Ppulse.compileSdk` is still honoured exactly, so a tagged release is built against the platform the
  project names, and the printed line about a substitution is the only sign a fallback happened.

A run waits for the slowest group — `:app` — instead of for the sum of the groups, and the groups
are cached: `gradle.properties` enables the build cache, so a run after the first on the same branch
restores compiled classes instead of compiling from scratch.

The tests themselves are the exception. **A `Test` task is never satisfied from the cache or skipped
as up to date in CI**: `.github/ci/no-cached-tests.init.gradle`, passed with `-I`, makes every test
task execute, and the unit-test job then counts the test result files the run wrote and fails if
there are none. A restored result would be a green run in which nothing ran, and at least one test
here reads a schema file that is not an input Gradle can see, so a change to it would not even
invalidate a restored result.

Pull-request runs are cancelled when a new push supersedes them; merges to `main`, tag runs and
manual release runs never are — the runs on `main` are what write the caches, and a cancelled
release publishes nothing.

## Publishing

```bash
git tag -a v1.7.1 -m "v1.7.1"
git push origin v1.7.1
```

The workflow builds the phone and watch APKs, names them `intervals-<tag>-debug.apk`,
`intervals-<tag>-release.apk`, `intervals-<tag>-wear-debug.apk` and `intervals-<tag>-wear-release.apk`,
generates release notes and attaches them to the release.

## Re-signing an existing release

If a release was published before signing was configured, run **Actions → Build and release → Run
workflow** on `main` with `release_tag` set to the existing tag (for example `v1.7.0`). The workflow
builds, signs and re-uploads the APKs with `overwrite_files: true`.
