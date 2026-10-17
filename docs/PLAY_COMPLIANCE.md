# Google Play submission preparation

Policy references checked on 17 October 2026. These repository changes prepare the app; they do not
complete declarations or accept agreements on behalf of the Play Console account owner.

## Privacy policy publication

1. Review [`legal/privacy.json`](../legal/privacy.json), including the maintainer name **Mr Cerise /
   MrCerise**, the existing project contact **security@nexalab.fr**, and retention of privacy support
   messages. The mailbox must be monitored and these practices must actually be followed. PULSE
   Interval Coach is named in the policy; use consistent app identity in the store listing.
2. Run `python scripts/generate_privacy_policy.py`. Check in the generated Android resources and
   public HTML pages. `--check` verifies that all versions match; do not hand-edit generated files.
3. Host [`privacy/index.html`](privacy/index.html) and [`privacy/fr.html`](privacy/fr.html) together on
   a public HTTPS site without login or geographic restrictions. If GitHub Pages serves this repo's
   `/docs` directory, the proposed English URL is `https://mrcerise.github.io/pulse-workout/privacy/`
   and the French URL ends in `fr.html`. **These are proposed paths, not verified live URLs.**
   Alternatively upload the two files to an existing static host. They contain no trackers, scripts
   or external dependencies.
4. Verify both pages in a signed-out browser, including their language links. Enter the live URL in
   Play Console. The phone and watch show the same text offline from Settings. Health Connect opens
   the phone policy directly, without onboarding or starting a workout.

Google requires an active public policy URL and an in-app policy:
[User Data policy](https://support.google.com/googleplay/android-developer/answer/18258653?hl=en).

## App content declarations

Complete these against the **actual release artifact**, SDKs and distribution settings:

| Declaration | PULSE evidence / information to provide |
| --- | --- |
| Health apps | Fitness coaching / exercise tracking and Health Connect. PULSE is not a medical device. |
| Health Connect: `READ_HEART_RATE` | Live heart rate in the player/dashboard and session summary statistics; optional. |
| Health Connect: `READ_STEPS` | Today's steps on the dashboard; optional. |
| Health Connect: `WRITE_EXERCISE` | Completed exercise sessions with interval segments; optional and controlled by Settings → Health. No heart-rate write permission. |
| Data safety | Review health/fitness, precise/approximate location, recordings and app content against Google's definitions and exceptions. Account for Android backup, Wearable Data Layer relay, user-initiated sharing and bundled SDKs. Local access is not automatically off-device collection; do not simply mark every permission as collection or assume “no collection” from offline-first architecture. |
| Foreground services | `specialUse` for the explicitly started interval timer; `location` for permission-granted runs. Remove obsolete `mediaPlayback` declarations once no active bundle on any track uses that permission. |
| Audience / rating / ads / app access | Declare the intended audience, complete IARC, declare no ads if still true, and explain there is no login. Health Connect must not be used by an app solely targeting children. |

References: [Health permissions](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en),
[Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en),
[Health Content and Services](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en).

## Store description: health disclaimer

Include the following in localized full descriptions. The reminder is also in Settings and the policy.
Describe Health Connect/device requirements accurately.

**English:** PULSE is a fitness coaching and interval timer app. It is not a medical device and does
not diagnose, treat, cure or prevent any medical condition. Consult a healthcare professional for
medical advice, diagnosis or treatment. Heart-rate, pace and distance readings may be approximate.

**Français :** PULSE est une application de coaching sportif et de chronométrage par intervalles.
Ce n’est pas un dispositif médical ; elle ne diagnostique, ne traite, ne guérit et ne prévient aucune
maladie. Consultez un professionnel de santé pour tout conseil médical, diagnostic ou traitement.
La fréquence cardiaque, l’allure et la distance peuvent être approximatives.

## Foreground service explanation and video

The timer must run for the user-selected duration, including silent/vibration-only sessions with the
screen off. Delaying or interrupting it would miss interval transitions and cues. The three-minute
`shortService` limit does not fit, and timing does not depend on continuous media playback or sensor
access. Android 14+ therefore declares `specialUse`, with this purpose in
`android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. Optional GPS runs additionally use `location`.
This declares the actual behavior; **Google Play still reviews and must approve the use case**.

Record a real-device demonstration:

1. Explicitly start a workout. Show the notification with remaining time and pause/stop controls.
2. Turn the screen off, show interval/cue continuation, then pause and stop from the notification.
3. Repeat with audio disabled to demonstrate timing independently of media playback.
4. Start a running workout, show the location disclosure/grant, then GPS distance/route tracking
   with the screen off. Stop and show that tracking ends. Also demonstrate denial/fallback.
5. Provide accessible video links in the Play Console foreground-service declaration.

References: [FGS declarations/videos](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en),
[Android specialUse](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use).

## Signing, phone/watch compatibility and API levels

- Accept the Play App Signing terms as the account owner or authorized user and confirm enrollment.
- The CI upload keystore must match Play's **upload certificate**. The delivered APK uses the
  **app signing key**, which may differ. Compare Play Console's SHA-256 certificates with
  `apksigner verify --print-certs <apk>` on the actual distributed APKs.
- Data Layer requires the same package name **and signing certificate** on phone and watch. Sharing
  a GitHub signing key does not prove compatibility with a Play install. Distribute both through Play
  with the same app signing key, or sign external APKs with that key if you retained it. If Google
  generated the app key, use Play-generated signed APKs where available; upload-key-signed APKs are
  not interchangeable updates. Do not change a live signing key merely to make these checks pass.
- The phone target is fixed to API 36 and Wear to API 35, independently of compile SDK discovery.
  The Play phone job requires compile SDK 36+, and `-Ppulse.playRelease=true` rejects a lower SDK.
  Re-check requirements before future releases.
- Wear bundles still need separate Play version codes/form-factor tracks; the train uploads only
  the phone. See [RELEASING.md](RELEASING.md).

References: [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en),
[Data Layer certificates](https://developer.android.com/training/wearables/data/overview#security-of-communications),
[Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en).

## Verification before submission

- Run the resource/reference checks, `python scripts/generate_privacy_policy.py --check`, app/watch
  JVM tests and Android lint. Inspect the signed release's merged manifest and target SDK.
- On Android 13 and 14+, open the policy from Settings and Health Connect, with onboarding unfinished
  and an already-running workout. It must open directly and must not interrupt the workout.
- Test GPS grant, denial, approximate-only access, revoked access and screen-off operation. Verify
  first-grant tracking and that stopping the session ends location access.
- Test microphone and Health Connect permissions. Delete a recorded cue and verify its private file
  is removed; imported media must remain untouched.
- Check the watch policy with rotary scrolling, standalone and with sync disabled. Test actual
  phone/watch artifacts installed from the intended distribution channels.
- Verify live policy URLs and support mailbox, complete declarations and review pre-launch reports.
  Source-only checks cannot certify these account/device/publication steps.
