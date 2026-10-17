# 1.8.5 — verification

Automatic version-tag releases upload the signed phone AAB to closed testing track
`4701389324980022660`, with draft status. The same phone APK/AAB, Wear OS APK/AAB, mappings and
checksums remain available in GitHub Releases.

Release text lives in `distribution/releases/1.8.5`: `github.md` for GitHub and `whatsnew/` for
Google Play. English, French, German, Spanish and Italian notes are staged once, validated against
Play's 500-character limit, and shared with both publishing jobs.

Checks: workflow actionlint, Python release/source/translation/privacy tests and static resource
checks locally; Android unit tests and signed builds run in CI before uploading. Release jobs stay
pinned to the workflow event commit, which must be in main's history.
