# v1.7.3 release verification

Version **1.7.3**, version code **19**, minimum SDK 26 on both APKs. No database migration, no new
permission on either app, no change to any sync payload (`PROTOCOL_VERSION` stays **1**, so a 1.7.2
phone and a 1.7.3 watch keep talking to each other), no dependency change and no Gradle plugin change.

## Changes

- **Renamed to Pulse Workout.** `app_name` in `values/strings.xml` is the only source of the name and
  it drives the launcher label, the welcome screen, the About card, the generated share image, both
  home-screen widgets, the notification channel and the manufacturer line Health Connect and Google Fit
  display. The four sites that *draw* a wordmark — `WelcomeScreen`, the About card, `SummaryImage`'s
  light and dark sheets, and the in-content lockup on the player's finish summary — now set "Pulse" in
  the brand colour bold and "Workout" in ink at medium weight, one size step down.
  The package, application id, resource names, artifact names, repository and Crowdin project are
  untouched, so an installed phone updates in place and translation history survives a rename. The
  watch app stays wordless by design (its label is `pulse_mark_content`), except that its share text now
  reads "Pulse Workout".
- **Smaller mark in the launcher icon.** The vector's group scale is the only measurement that matters
  here, and it went from `0.234375` (60 dp of the 108 dp adaptive viewport) to `0.1953125` (50 dp),
  centred at (29, 29). Foreground and monochrome, phone and watch — four files. Themed icons and
  legacy masks crop the same 72 dp safe zone, so a smaller mark is what makes the icon stop shouting.
  `scripts/check_brand.py` derives the dp figure from the vector instead of trusting the number, and
  compares it with `docs/DESIGN.md` §10, so artwork and reference cannot drift apart unnoticed.
- **A language the user chooses** — Settings → Appearance → Language: *Follow system* (default),
  English, French, German, Spanish or Italian.
  - `AppLanguage` (`data/Prefs.kt`) is the stored choice: one nullable BCP-47 tag per entry, `null` for
    *follow system*, `de-DE`/`es-ES`/`it-IT` carrying the region their packaged folder needs
    (`values-de-rDE` matches a bare `de` request not at all). Stored as the enum name under
    `app_language`, unknown values ignored rather than fatal.
  - `locale/AppLocale.kt` does the work. `localizedContext` wraps a base context in a
    `createConfigurationContext` whose locale is pinned; `Localized` re-provides `LocalContext` at the
    composition root, which is how a Compose app changes language on the tap — no `Activity.recreate()`,
    so the session's coroutines, the service connection and the scroll positions all survive.
    `localizedContext` returns the base context untouched for *follow system*, so the default path is
    exactly the pre-1.7.3 path.
  - `applySystemLocales` also writes `LocaleManager.applicationLocales` (Android 13+), fired from the
    app scope rather than the composition so it survives a process restart and any config change. That
    is what pulls home-screen widgets, notifications and `RemoteViews` along with the screens; on
    Android 8–12 the platform has no such API, so those surfaces keep following the device while every
    screen follows the app.
  - The device locale is never modified. The app does not declare `android:localeConfig` — that is the
    API 33+ platform mechanism whose storage and system Settings entry cannot cover Android 8–12, where
    the app's own picker is the only way in — so `AppLocale` keeps the value in `UserPreferences` and
    calls `LocaleManager.setApplicationLocales` itself to keep the platform-drawn surfaces in step. That
    call is documented as possibly producing a configuration change in the calling app, so
    `MainActivity` now declares `locale|layoutDirection` in `android:configChanges` — the change is handled
    in place, the way `uiMode` already is for the dark-mode switch, and a running session is not torn down.
    The preference is written from the app scope before the platform is told, so the screens never depend
    on the platform call at all.
- **`compileSdk` is resolved, not hardcoded.** The root `build.gradle.kts` picks the Android platform —
  explicit `-Ppulse.compileSdk` (what `.github/actions/setup-pulse-build` exports) wins; otherwise API 37
  when it is installed, otherwise the newest installed API at or below it, otherwise 37 unchanged; a
  substitution is printed. `:app` and `:wear` read that one value, so they can never be built against
  different platforms. This is what makes a bare `./gradlew assemble` work where
  `platforms;android-37` is missing — the CodeQL `Analyze (java-kotlin)` autobuild step failed exactly
  that way, on AGP's attempted SDK download.
- **`WatchSessionRecorder` contains its own fan-out.** `onSessionFinished` and `onLiveSnapshot` wrap
  both calls, so a link that throws can neither stop the watch's own history write nor unwind into the
  engine's teardown — previously the containment lived in the *test*, not in the code.

## Automated checks

Run locally (no Android SDK needed):

```bash
python3 scripts/check_ui.py          # 388 app + 63 wear strings, exact EN/FR parity, 603 names
python3 scripts/check_brand.py       # seven brand vectors, 50 dp launcher mark derived from the XML
python3 scripts/kotlin_xref_gate.py  # 0 problems
python3 scripts/contrast_check.py    # 125 pairs; no token changed here
git diff --exit-code -- docs/CONTRAST.md
```

With `pip install tree-sitter tree-sitter-kotlin`, `python3 scripts/kotlin_parse_gate.py` parses all
93 Kotlin files with 0 syntax errors.

CI on the release pull request runs those gates in `resource-checks` and the five test groups in the
`unit-tests` matrix. Pushing the `v1.7.3` tag builds four APKs (phone/wear × debug/release), signs the
releases when the keystore secrets are present, and attaches
`pulse-v1.7.3-debug.apk`, `pulse-v1.7.3-release.apk`, `pulse-v1.7.3-wear-debug.apk` and
`pulse-v1.7.3-wear-release.apk` to the GitHub release.

What the unit tests cover for this change:

- every language the picker offers names a `values-…` folder the app packages, every tag is a
  well-formed locale whose region matches that folder, and no two entries share a tag (`AppLanguageTest`);
- the stored names are the ones DataStore holds, so a rename cannot silently reset a user's choice;
- no `values-*/strings.xml` declares a key the English source does not have — the rule that keeps
  Crowdin's partial exports harmless;
- a finished watch session reaches the history *and* the link, and a link that throws on either the
  log or a live snapshot leaves the local record written and the exception inside the recorder
  (`WatchSessionRecorderTest`);
- EN/FR key parity for the three strings the picker adds, enforced per module by `check_ui.py`.

## Maintainer/device checks

Not claimed as done by the source changes alone — no phone, watch or launcher exists in the environment
the change was written in:

- Look at the icon on a real launcher, and in the settings list, the app drawer and Themed icons: the
  mark should read as a 50 dp figure inside the circle/squircle rather than fill it, on the light and
  monochrome variants both.
- Set *Language* to French on an English device and page around without restarting: the strings should
  change under the finger, including on the settings screens themselves. Set it back to
  *Follow system* and confirm the device language returns. Force-stop and relaunch: the choice must
  survive.
- With a non-device language pinned, check the surfaces that are not Compose: the two home-screen
  widgets, the running-session notification, the shortcut long-press menu and the share sheet title.
  On Android 13+ they should follow the app; on Android 8–12 they follow the device, which is the
  platform's limit, not a regression.
- Start a session, then switch the language mid-workout: the session must keep running, the timer
  untouched, and the player's strings change.
- Run a session *on the watch only* (sync off), finish it, and confirm the history holds it while the
  phone — unreachable — receives nothing until the link is enabled and the queue drains.
- On a 1.7.2 phone paired with a 1.7.3 watch (and the other way round): library sync, live mirroring
  and remote control must behave exactly as before, since no payload changed.
- Install over a 1.7.2 build rather than uninstalling first: the app must update in place with the
  launcher label changing to Pulse Workout, the library and history intact, and no re-pairing.
- Confirm the CodeQL scan: the `java-kotlin` analyze job's autobuild should now compile
  `:app`/`:wear` on a runner without API 37 (it prints the substitution line) instead of failing on
  the missing platform.

---

See also: [CHANGELOG.md](../CHANGELOG.md#173--2026-10-15) · [DESIGN.md §10](DESIGN.md) · [ARCHITECTURE.md](ARCHITECTURE.md) · [TRANSLATING.md](TRANSLATING.md) · [VERIFICATION.md](VERIFICATION.md) · [RELEASING.md](RELEASING.md)
