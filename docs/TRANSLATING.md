# Translating PULSE

Join **https://crowdin.com/project/pulse-workout**, choose a language and translate or review
strings. No Android development environment is needed. Ask the project maintainer to enable a
language if it is not listed.

Keep placeholders such as `%1$s`, `%1$d` and `%2$d` intact (they may be reordered). Preserve
newlines, XML markup and Android escaping. Keep short button labels short. Do not translate
resource names or the PULSE brand. Review translations in context on a device when possible.

## How synchronization works

`crowdin.yml` maps the English source `app/src/main/res/values/strings.xml` to Android resource
folders using Crowdin's `%android_code%` placeholder, including region/script qualifiers.
The workflow uploads sources after relevant changes on main and downloads approved translations
daily or on manual dispatch. Unapproved/missing translations export as English, keeping the
existing complete French table compatible with `scripts/check_ui.py`. Android also falls back to
English for missing resources. All locale folders are packaged, not just English and French.

Translation updates open a PR against main, never merge automatically, and go through the same
unit tests as code changes. Source wording should be edited in Git, not in Crowdin. Do not rename
existing resource keys unnecessarily: doing so loses translation history.

## Maintainer setup (required before first sync)

1. In the Crowdin project settings, confirm English is the source language, enable French and
   any desired target languages, and copy the **numeric project ID** (not `pulse-workout`).
2. Set these GitHub repository Actions secrets through the repository settings:
   - `CROWDIN_PROJECT_ID`: the numeric ID.
   - `CROWDIN_PERSONAL_TOKEN`: a Crowdin token with access to this project's source upload,
     translation import/export and build operations.
   - `CROWDIN_GITHUB_TOKEN`: a dedicated bot's fine-grained GitHub token restricted to this
     repository, with Contents and Pull requests read/write. A bot token is necessary because
     PRs created with the default `GITHUB_TOKEN` do not trigger the PR CI workflow.
3. Run **Crowdin translations** on **main**, selecting **seed_translations** once to import
   the existing French translations. Imported suggestions are not automatically approved;
   review/approve them in Crowdin before merging exported changes. Inspect the first PR for
   unintended replacement of existing French wording with English.
4. Subsequent scheduled runs upload English and download approved translations only; they do
   not reimport Git translations. Review PR diffs and CI before merging.

Secrets must never be put in `crowdin.yml`, committed, or shared in issues. Missing secrets fail
with setup instructions; they do not affect normal PR tests or the release build. Crowdin is
build-time tooling only: the installed app remains offline-first with no Crowdin SDK or calls.
