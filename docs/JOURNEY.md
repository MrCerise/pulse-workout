# Phone fitness journey

Journey is the phone home destination. Free Workouts retains the existing dashboard, editable
library, builders, history, goals and planner. Completing a free workout never earns journey
credit, even when its plan id matches a journey node. Free Workouts is accessible through its own
navigation destination; Journey and its setup contain no Free Workouts shortcut.

## Content and progression

Each of the three levels begins with four foundation chapters of six sessions. Their original
workouts and ids are unchanged. Further chapters continue without a predefined end, using
deterministic local generation from the exercise groups. Movement selection/order, work/rest
timings and rounds vary within the chosen level's range; chapter number never escalates effort
without limit. A session id reconstructs the same workout after restart in either language.
The lazy path builds visible sessions on demand. More chapters appear as recommendations advance,
and Explore more sessions lets users browse ahead without completing earlier chapters.
Every session includes preparation, rest, a cooldown, movement guidance and easier variations.
The catalogue and UI ship in English and French; other languages fall back to English.

The recommendation is the earliest incomplete session in the selected level. Every node and level
remains selectable. After all 24 sessions, readiness offers promotion or continued practice.
Completing the advanced foundation continues into new advanced sessions. Staying at any other
level also continues generating sessions, while readiness remains an optional promotion.
Timer completion records participation, not objectively measured fitness improvement.

**Release content gate:** the authored exercise selection, timing progression and level labels
still need review by a qualified fitness content reviewer before public release. Automated timeline
validation does not replace that review.

## Records and weekly rhythm

Room migration 3 → 4 preserves existing rows and defaults their source to FREE.
Journey launch carries an explicit node id and catalogue version, persisted before engine start.
The timer, coaching, player and foreground service are reused. Journey attempts stay out of
free workout aggregate queries, Health Connect export and phone/watch mirroring or control.
The watch catalogue and protocol are unchanged.

A normally completed session with every interval completed and none skipped earns a credit.
Attempt updates and the credit insert share one transaction. Session UUIDs deduplicate credits.
Replays have new UUIDs but training-day counting deduplicates local calendar dates.
Interrupted and stopped attempts remain stored without earning completion.

Weeks run Monday–Sunday in the timezone saved at enrollment. The enrollment week is practice.
The initial goal defaults to three days; two through five are supported. Changes take effect
the following Monday without rewriting earlier targets. An unfinished current week retains the
previous streak; a missed closed week breaks it without removing path completion.
Selected days guide reminders, not eligibility: training on any day earns credit.

Optional reminders use the existing deferrable 15-minute worker, check the selected days/time
and post at most once per calendar date. Posting is serialized with session completion; completing
a journey session cancels the journey notification. A reminder opens Journey without starting a
session. Android notification permission and background scheduling still apply.

## Validation

Tests keep the existing stack: JUnit4, MockK, Compose device tests and the manual AppContainer.
The journey repository takes a database directly, allowing isolated on-device databases without
replacing the app object graph. SQLite JDBC verifies migration SQL on the JVM.
Instrumentation uses Espresso 3.7.0 and AndroidX JUnit 1.3.0; Espresso 3.6.1 cannot inject input on
the Android 17 emulator because it reflects InputManager.getInstance. See the
[AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0).

- JVM: catalogue uniqueness/playability in both languages; completion eligibility; recommendation
  gaps; duplicate training dates; practice weeks; missed weeks; target history; fixed timezone/DST;
  continuing generation, stable generated ids, varied recipes and bounded effort in distant chapters.
- SQLite: execute the production 3 → 4 migration against exported v3 and compare its tables with a
  fresh exported v4 schema, preserving a legacy session and its FREE default.
- Device: isolated Room databases verify restart persistence, duplicate completion, free workout
  isolation, interrupted attempts, target changes, free level selection, readiness and reminders;
  generated-session completion and continuation after reopening the database.
- Compose device tests cover setup, saved-form restoration, explicit preview/start, French,
  large text, dark/high contrast and reduced motion.

Verified on 2026-10-30: debug app and test APK builds, phone lint, all 106 phone unit tests,
22 Wear regression tests, and all nine journey device tests on the API 37 phone emulator.
The winding path, expansion into Chapter 5 through Explore more sessions, and the generated
workout preview were also verified on the phone. Journey and setup contain no in-page
Free Workouts actions.

Run :app:testDebugUnitTest, :app:assembleDebug, :app:assembleDebugAndroidTest and :app:lintDebug.
Run JourneyPersistenceTest and JourneyUiTest on a phone emulator with AndroidJUnitRunner.
Manually check the path, normal session completion, screen-off coaching, back navigation and
level switching before release. Physical exercise execution and TalkBack speech need human review.
