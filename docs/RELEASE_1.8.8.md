# 1.8.8 — verification

Feature release: the Wear OS app on Wear Compose Material 3 with a home dashboard, workout details,
favorites synced to the phone, a tile and optional voice cues; the phone sends its weekly dashboard
to the watch; compact QR codes for workouts whose text overflows a full code; an AI prompt without
exercise instructions.

`PROTOCOL_VERSION` stays at 1: the dashboard and favorite paths are additive, and a watch that has
never received a dashboard falls back to its own history.

Release text lives in `distribution/releases/1.8.8`. Android unit tests and signed builds run in CI
before uploading; the tag upload goes to closed testing track `PreRelease Beta` as a draft.
