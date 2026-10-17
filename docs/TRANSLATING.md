# Translating PULSE

PULSE is translated by the community on [Crowdin](https://crowdin.com/project/pulse-workout). No
Android development environment is needed — sign in, pick a language and start translating or
reviewing. Ask a maintainer to enable a language if it is not listed yet.

- [For translators](#for-translators)
- [Translation rules](#translation-rules)
- [How synchronization works](#how-synchronization-works)
- [Maintainer setup](#maintainer-setup)
- [Adding a string as a developer](#adding-a-string-as-a-developer)

## For translators

1. Open [crowdin.com/project/pulse-workout](https://crowdin.com/project/pulse-workout) and choose a
   language.
2. Translate or review the strings. Source wording itself is edited in Git, not in Crowdin — if a
   source string is wrong, open an issue or a pull request instead.
3. Approved translations are exported back as a pull request against `main`, reviewed like any other
   change, and shipped in the next release.

## Translation rules

- **Keep placeholders intact:** `%1$s`, `%1$d`, `%2$d` and friends. They may be reordered, so make
  sure the sentence still reads correctly in your language.
- **Preserve newlines, XML markup and Android escaping** (`\n`, `\'`,
  `<b>…</b>`, `&amp;`).
- **Keep short button labels short.** Several labels sit in a 48 dp control or a navigation bar.
- **Do not translate resource names** (the `name` attribute) or the PULSE brand.
- **Review in context when you can** — install a debug build and look at the screen the string
  belongs to.

## How synchronization works

`crowdin.yml` maps the English source `app/src/main/res/values/strings.xml` to Android resource
folders using Crowdin's `%android_code%` placeholder, which covers region and script qualifiers:

```yaml
files:
  - source: /app/src/main/res/values/strings.xml
    translation: /app/src/main/res/values-%android_code%/strings.xml
    languages_mapping:
      android_code:
        fr: fr-rFR
    type: android
    skip_untranslated_strings: false
    export_only_approved: true
```

- Translations use region-qualified folders: `values-fr-rFR`, `values-de-rDE`, `values-es-rES`
  and `values-it-rIT`, on both phone and watch. Keep one table per locale. The language mapping
  exports French to `values-fr-rFR`. Set the same `android_code` mapping for French to `fr-rFR`
  in Crowdin's **Project Settings → Languages** for the GitHub integration. See
  [Crowdin language mapping](https://support.crowdin.com/project-settings/languages/).
- Crowdin's native GitHub integration reads that configuration, uploads sources from `main` and opens
  pull requests with approved translations. Authentication is managed by the integration — no project
  id, API token or GitHub token belongs in this file or in Actions secrets.
- Crowdin exports unapproved and missing translations as English. Review these exports before
  merging: `scripts/check_ui.py` requires complete packaged translation tables and rejects copied
  English sentences. Android itself falls back to English for missing resources.
- **Every locale folder is packaged**, not only English and French.
- The app offers a language of its own in Settings → Appearance, defaulting to *Follow system*. An offered language is an `AppLanguage` entry whose tag names a packaged folder (`fr-FR` → `values-fr-rFR`, `de-DE` → `values-de-rDE`), and `app/src/test/java/com/mrcerise/intervalcoach/AppLanguageTest.kt` fails the build when a tag and a folder stop agreeing — so enabling a language in the Crowdin project is not by itself enough to get it into that picker. A packaged-but-unoffered locale is still used when the *device* asks for it.
- Translation updates open a PR against `main`, never merge automatically, and go through the same
  unit tests as code changes. Keep automatic merging disabled.
- Avoid renaming existing resource keys: doing so loses translation history.

## Maintainer setup

Required once, before the first synchronization:

1. In the Crowdin project settings, confirm English is the source language and enable French and any
   other target language you want.
2. In **Integrations → GitHub**, authorize the Crowdin GitHub app for `MrCerise/pulse-workout`,
   select **main** as the source branch, and use the repository-root `crowdin.yml` configuration.
3. Configure the integration to push translations to **l10n/crowdin** and open PRs against **main**
   rather than pushing directly. Enable automatic source synchronization and choose a translation
   synchronization schedule in Crowdin. Keep automatic merging disabled.
4. Import the existing French `app/src/main/res/values-fr-rFR/strings.xml` translations through Crowdin's
   translation upload **before** the first export. Do not automatically approve imported suggestions —
   review and approve them in Crowdin first, and inspect the first PR for unintended replacement of
   existing French wording with English.
5. Confirm the translation PR triggers the normal resource checks and unit tests. Review diffs and CI
   before merging; later syncs should not re-import Git translations.

The old Crowdin Actions workflow has been removed so the two mechanisms cannot compete. Once the
integration works, delete the unused `CROWDIN_PROJECT_ID`, `CROWDIN_PERSONAL_TOKEN` and
`CROWDIN_GITHUB_TOKEN` repository secrets and revoke any token dedicated to that workflow. Secrets
must never be committed or shared in issues.

Crowdin is build-time tooling only: the installed app contains no Crowdin SDK and remains
offline-first.

## Adding a string as a developer

- Add every new user-visible string to `app/src/main/res/values/strings.xml` and translate it in
  every packaged locale. Use region-qualified folders, such as `values-fr-rFR`. Do not fill entries with English
  placeholders. Obtain the missing translations before merging.
- Never hardcode user-visible text in Compose code.
- Use plurals for counts and indexed format arguments for dynamic text. Use the localized context's
  resource configuration for date formatting so the in-app language also controls month/day names.
- `python3 scripts/check_ui.py` checks all packaged string and plural tables for coverage, format
  arguments and copied English sentences. Run `python -m unittest scripts.tests.test_translations`
  to exercise those checks.

See [CONTRIBUTING.md](../CONTRIBUTING.md) for the rest of the workflow, and
[RELEASING.md](RELEASING.md) if you are cutting the release that ships the new locale.
