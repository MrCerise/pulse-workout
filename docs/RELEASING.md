# Releasing Interval Coach

The same signed phone AAB/APK is published to GitHub Releases and, when enabled, Google Play.
Wear OS AAB/APK files, both R8 mappings and SHA-256 checksums are also attached to GitHub Releases.

## Automatic tag releases

Push an existing source commit under a new version tag, for example:

```bash
git tag -a v1.8.5 -m "v1.8.5"
git push origin v1.8.5
```

**Build and release** calls the reusable **Release train**. It checks the tagged source, runs tests,
then builds signed phone and watch artifacts in parallel. A tag upload goes to **Play closed
testing track `PreRelease Beta` as a draft**; it does not publish to testers or production automatically.

GitHub Release publishing and Play uploading are independent after the build. A Play credential,
permission or API failure does not prevent the GitHub assets from being published. The overall
workflow still fails so a failed Play upload is visible. GitHub Release assets are available for
all channels and Play statuses, including internal testing and drafts.

A `v1.8.4` tag publishes a normal GitHub Release; a suffix such as `v1.8.4-rc.1` marks a prerelease.
Manual testing trains without an existing tag get a distinct tag, such as
`v1.8.4-internal.10804190`, so they cannot overwrite the stable release.

## Version names and codes

Both `app/build.gradle.kts` and `wear/build.gradle.kts` keep matching local defaults. A tagged
build uses the **tag's version and exact commit**. For a manual tagged build, select that tag under
**Run workflow → Use workflow from** as well as supplying `release_tag` (or use
`gh workflow run release-train.yml --ref v1.8.4 -f release_tag=v1.8.4`). The requested tag must match
the workflow event's commit, and that commit must already be in `main`'s history.
The train validates its inputs before starting a Gradle runner or decoding a signing key.

For tag builds, an empty `version_code` derives a code using the existing numeric tag scheme:

```text
major * 10,000,000 + minor * 100,000 + patch * 1,000 + workflow run number
```

For example, `v1.8.4` on workflow run 190 produces `10804190`. Stable tags also use the run slot,
so moving from a prerelease to a stable tag does not reset the code to a lower value. Automatic
codes require minor/patch values below 100 and a run number from 1 to 999; the workflow fails rather
than wrapping and reusing a slot. An explicit code overrides the calculation.

**Check Play's existing codes before uploading.** No offline formula can verify the highest code
already uploaded by another workflow or manually. A Play code must be an integer from 1 to
2,100,000,000, above every prior upload across tracks and drafts. If Play already has a higher code,
or an automatic slot is exhausted, dispatch the workflow with an explicit higher `version_code`.
Manual trains without a tag require an explicit code.

Re-running the same workflow run keeps its original code. If Play has already accepted that code,
start a new dispatch with a new code, or manage the accepted release in Play Console. To promote
exactly the tested bundle, use Play Console rather than rebuilding it.

## Signing setup

Add these **repository** secrets in **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the upload keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Existing key alias |
| `ANDROID_KEY_PASSWORD` | Password for that key |

Never commit the key or passwords. Use the existing Play upload key for an already published app.
PowerShell can encode a keystore with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("pulse-release.jks"))
```

A missing signing secret fails the build. The decoded keystore lives in the runner's temporary
directory and is removed even if the build fails.

## Google Play setup

Before submission, complete [Play compliance preparation](PLAY_COMPLIANCE.md): publish the privacy
policy, review Data safety/health/foreground-service declarations and supply the store disclaimers.
`python scripts/generate_privacy_policy.py --check` checks the public and in-app policy copies.

1. Create the Play Console application for `com.mrcerise.intervalcoach`, complete its setup, enroll
   in Play App Signing and upload the first signed phone AAB manually. An artifact-only train can
   produce this first bundle.
2. Enable the Google Play Android Developer API, create a service account and JSON key, then give
   that account access to this app and the required release permissions in Play Console. See the
   [API setup guide](https://developers.google.com/android-publisher/getting_started).
3. Add the full plain JSON key as **`ANDROID_SERVICE_ACCOUNT_JSON`**, at repository level or in
   the matching GitHub environment. It is **not base64**. The signing secrets above stay at
   repository level because build jobs run before the Play environment.
4. Configure **`play-testing`** for testing channels and **`play-production`** for production.
   If branch/tag restrictions or reviewers are enabled, allow the refs intended for release.
   Create the selected testing track and configure its testers in Play Console.

Phone and watch builds require compile SDK 37 (Compose 1.12) and phone releases use `-Ppulse.playRelease=true`; the phone targets API 36
and Wear OS targets API 35 independently of the installed compile SDK.

**Certificate compatibility:** GitHub APKs use the local keystore, while Play-delivered APKs use
Play App Signing. These certificates can differ. Phone/watch Data Layer requires matching installed
signatures. See [signing checks](PLAY_COMPLIANCE.md#signing-phonewatch-compatibility-and-api-levels)
before mixing distribution channels.

## Manual dispatch

Use **Actions → Release train → Run workflow**:

| Input | Behavior |
| --- | --- |
| `channel` | `closed` (default), `internal`, `beta` (open testing), or `production` |
| `play_track` | Closed testing only: defaults to the exact API name `PreRelease Beta`; override for another track |
| `release_tag` | Optional existing tag; builds that tag's commit and uses its version |
| `version_name` | Plain `MAJOR.MINOR.PATCH`; empty reads the tag or repository default |
| `version_code` | Explicit increasing Play code; may be empty only for a tag with an automatic slot |
| `publish_to_play` | Enabled by default; disable for GitHub Release + signed artifacts only |
| `release_status` | `draft` (default) for review, or `completed` to distribute to the selected track |
| `staged_rollout_percent` | Integer 1-100; below 100 requires production + completed |
| `run_tests` | Enabled by default; production always runs tests |

`beta` is not the closed-testing channel. Select `closed` and supply the actual Play track ID for
closed testing. Production staged rollouts below 100% become `inProgress` with `userFraction`.
Finish draft releases and send them for review in Play Console when ready.

Use the track name returned by the Android Publisher API, including its spaces and capitalization.
The numeric ID from a Play Console URL is not the API track name. `PreRelease Beta` is a custom
closed-testing track; the reserved API track `beta` means open testing.

If only the Play upload failed before accepting the bundle, use **Actions → Retry Play upload →
Run workflow** from `main`, and supply the existing GitHub Release version (for example `1.8.5`).
It checks that the tag's commit is in main's history, downloads the published phone bundle/mapping,
verifies their exact SHA-256 checksums, and uploads them to `PreRelease Beta` as a draft with the
localized notes. It does not rebuild or change the GitHub Release. If Play already accepted a
version code, manage that version in Play Console instead of uploading it again.

## Release notes

Before tagging a version, create `distribution/releases/MAJOR.MINOR.PATCH/github.md` and a
`whatsnew/` subdirectory containing `whatsnew-en-US`, `whatsnew-fr-FR` and any other supported
locales. Version 1.8.5 includes all five app languages. Filenames have no extension; Play notes
must contain 1–500 Unicode characters per language. Notes describe actual changes, without promotion.

The validation job stages these files once in a `release-notes-<run ID>` artifact. GitHub uses
`github.md` as its release body alongside generated commit/PR notes. Google Play receives the same
version's localized files through `whatsNewDirectory`. Missing or invalid notes stop the train
before signed builds start. Keep previous version directories so tagged releases remain reproducible.

**Actions → Build and release** remains a shortcut: set `release_tag` to rebuild an existing tag,
optionally override `version_code`, and toggle `publish_to_play`. An empty tag runs ordinary CI.
If a release tag already points to another commit, the train fails rather than attaching binaries
built from the wrong source. Select that tag in **Use workflow from** too, or choose a new version.
Source-validation and build jobs always check out the immutable workflow event SHA. A text input
cannot switch the runner to another revision while retaining the default branch's cache scope.

Wear OS bundles are supplied on GitHub for manual distribution. Only the phone AAB and its mapping
are uploaded to Play automatically. Phone and watch currently share a version code; automated Wear
publishing needs distinct codes and form-factor tracks such as `wear:production`.

## Artifacts and optimization

Each release produces:

```text
interval-coach-<version>-phone-release.apk
interval-coach-<version>-phone-release.aab
interval-coach-<version>-phone-mapping.txt
interval-coach-<version>-wear-release.apk
interval-coach-<version>-wear-release.aab
interval-coach-<version>-wear-mapping.txt
SHA256SUMS
```

Release APKs are ready for direct installation; AABs are for Play distribution. Debug APKs are no
longer built as release assets. R8 mappings remain available for diagnosing each exact release.

- One reusable train replaces the two separate APK/AAB release implementations.
- Static resource, privacy, brand, contrast and Python checks run once before the release builds.
- Phone and Wear OS build in parallel. Each platform's tests and packaging share one Gradle
  invocation and runner; JVM tests run on the phone runner only.
- Compiled work stays cached. CI test tasks always execute and must produce fresh result files.
- Already-compressed APK/AAB artifacts use compression level 0 and are uploaded once per platform.
  The GitHub and Play jobs download those exact artifacts, with no second build or combined upload.
- Run artifacts are retained for 30 days; published GitHub Release assets remain on the release.
- Release trains serialize across Play channels and are never cancelled while running. GitHub may
  replace an older pending run, so wait for one train to finish before queueing another.
- Ordinary CI keeps its three parallel unit-test groups and runs static checks once. Marketing
  assets, screenshot-editor changes and documentation-only changes skip Android CI; generated
  privacy pages and runtime brand vectors remain in scope.
- No release is started by an ordinary push to `main`. GitHub Release tags created with the workflow
  token do not trigger a second tag workflow, avoiding duplicate builds.

## Release checklist

1. Merge the intended source into `main` and keep phone/watch version defaults aligned.
2. Update the changelog and release verification note when preparing a release.
3. If schemas or exchange formats changed, complete their migrations and compatibility checks.
4. Regenerate privacy/contrast files if their sources changed and let CI pass.
5. Verify signing certificates, Play credentials and the next version code.
6. Push the new version tag or dispatch the train. Inspect both publishing jobs, then review the
   closed-testing draft in Play Console before releasing it to testers.
