# 1.9.0 — verification

Phone feature release: Journey is the default home, with three fitness levels, 72 foundation
sessions, continuing locally generated chapters, optional reminders and weekly consistency
streaks. Free Workouts remains a separate navigation destination.

Room migration 3 → 4 adds explicit session origin and Journey records. Existing sessions default
to FREE. Journey context is stored before timer start; completion and credit share a transaction.
No watch protocol change or generated-workout synchronization is introduced.

Verified locally on 2026-10-30:
- Debug phone APK and instrumentation APK builds; phone lint; git diff whitespace checks.
- All 106 phone unit tests, including exported-schema migration comparisons and generation tests.
- All nine Journey device tests on an API 37 phone emulator: persistence, free-mode isolation,
  generated-session continuation, setup restoration, French, large text and preview/start.
- The existing 22 Wear regression tests passed during feature verification.
- Manual phone check: Explore more sessions appends Chapter 5 and opens its workout preview.

Exercise content, physical execution and TalkBack speech remain manual review items documented
in JOURNEY.md. Local validation does not establish measured fitness improvement.

Release notes live in distribution/releases/1.9.0. Signed release builds and all module unit tests
run again in CI. Tag publication uploads the phone bundle to PreRelease Beta as a draft; it does
not publish to testers or production automatically.
