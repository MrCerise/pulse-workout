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
Crowdin's native GitHub integration reads this configuration, uploads sources from main and
opens pull requests with approved translations. The file mapping exports unapproved/missing
translations as English, keeping the existing complete French table compatible with
`scripts/check_ui.py`. Android also falls back to English for missing resources. All locale
folders are packaged, not just English and French.

Translation updates open a PR against main, never merge automatically, and go through the same
unit tests as code changes. Source wording should be edited in Git, not in Crowdin. Do not rename
existing resource keys unnecessarily: doing so loses translation history.

## Maintainer setup (required before first sync)

1. In the Crowdin project settings, confirm English is the source language and enable French
   and any desired target languages.
2. In **Integrations → GitHub**, authorize the Crowdin GitHub app for
   `MrCerise/pulse-workout`, select **main** as the source branch and use the repository-root
   **crowdin.yml** configuration. Authentication is managed by the integration; no project ID,
   API token or GitHub token belongs in this file or in Actions secrets.
3. Configure the integration to push translations to **l10n/crowdin** and open PRs against
   **main**, rather than pushing directly to main. Enable automatic source synchronization and
   choose a translation synchronization schedule in Crowdin. Keep automatic merging disabled.
4. Import the existing French `app/src/main/res/values-fr/strings.xml` translations through
   Crowdin's translation upload before the first export. Do not automatically approve imported
   suggestions; review/approve them in Crowdin before merging exported changes. Inspect the
   first PR for unintended replacement of existing French wording with English.
5. Confirm the integration's translation PR triggers the normal resource checks and unit tests.
   Review PR diffs and CI before merging; subsequent syncs should not reimport Git translations.

The old **Crowdin translations** Actions workflow is removed to avoid competing synchronization.
Once the integration works, remove unused `CROWDIN_PROJECT_ID`, `CROWDIN_PERSONAL_TOKEN` and
`CROWDIN_GITHUB_TOKEN` repository Actions secrets and revoke any tokens dedicated to that workflow.
Secrets must never be committed or shared in issues. Crowdin is build-time tooling only: the
installed app remains offline-first with no Crowdin SDK or calls.
