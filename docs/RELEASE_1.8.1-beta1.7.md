# 1.8.1-beta1.7 — verification

Changes: library toolbar + create FAB, number-scroll duration wheels, bottom-sheet inset fix
(AI workout option visible), uniform Settings spacing with larger gap before Voice & audio,
centred modern player transport, robust image sharing (explicit URI grants + text fallback).

Checks: scripts/check_ui.py passes locally; Gradle unit tests/build run in CI on the PR and tag.
The two reported crashes (Health Connect, Voice & audio) were confirmed by the reporter as not
app bugs and are untouched.
