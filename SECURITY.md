# Security policy

PULSE stores workout history, heart-rate readings and other health data on people's phones, so we'd
much rather hear about a security problem from you than read about it somewhere else. Thanks for
taking the time to report it.

- [Supported versions](#supported-versions)
- [How to report](#how-to-report)
- [What to include](#what-to-include)
- [What happens next](#what-happens-next)
- [Scope](#scope)
- [Out of scope](#out-of-scope)
- [Testing rules](#testing-rules)
- [Disclosure and credit](#disclosure-and-credit)

## Supported versions

We're a small project and we only patch the latest release. If you find something in an older
version, check whether it still happens on the current one. If you can't check quickly and the issue
looks serious, send the report anyway — we'd rather get it now.

| Version | Supported |
| ------- | --------- |
| 1.7.x (latest) | :white_check_mark: |
| 1.6.x and older | :x: |

The latest release is always listed on the
[releases page](https://github.com/MrCerise/pulse-workout/releases/latest).

## How to report

**Please don't open a public issue, pull request or discussion for a security problem.** Use one of
these private channels instead:

1. **GitHub Security advisories** (preferred):
   [Report a vulnerability](https://github.com/MrCerise/pulse-workout/security/advisories/new).
   We can discuss the report, work on a fix and publish an advisory in the same place.
2. **Email:** **security@nexalab.fr**. If the issue is urgent, start the subject line with
   `[CRITICAL]` — for example `[CRITICAL] PULSE: backup import reads arbitrary files`.

### What counts as urgent

Flag your report as critical if it involves any of the following:

- code execution through a workout file, backup, shared file or deep link;
- another app being able to read PULSE's private data or health records;
- a way around Android permissions or Health Connect permissions;
- anything affecting our signing keys, the APKs on the Releases page or the release workflow;
- signs that the issue is already being exploited.

## What to include

Don't worry about making the report perfect — a rough report we can follow is better than a polished
one that arrives a week later. These details help:

- the PULSE version (Settings shows it), or the commit you built from;
- your Android version and device model;
- what is wrong and where you think it lives in the app;
- steps to reproduce, or a small proof of concept;
- what you expected to happen and what actually happened;
- how bad you think it is, and what an attacker would need (another installed app, physical access,
  a crafted file, …).

Please use test data. Don't send real workout history, an unredacted backup, health records, tokens
or keys. Strip anything personal out of logs and screenshots. If you have come across a leaked secret
(a signing key, for example), tell us it exists and where you found it, but don't paste it into the
report.

## What happens next

The project is maintained in people's spare time, so we can't promise fixed response times. Here's
what we aim for:

- We reply to confirm we got your report, usually within a few days.
- Once we've reproduced it, we tell you whether we see it as a vulnerability and roughly how long a
  fix might take.
- We keep you updated while we work on it, and we may ask you to test the fix.
- When the fix ships, we mention it in the release notes and publish a GitHub security advisory if
  it's warranted.

If you haven't heard back after a week, feel free to send a reminder. Sometimes things get lost.

## Scope

Problems in this repository and in the APKs we publish, for example:

- workout JSON import, backup import and export;
- how the app stores its data and who can read it;
- shared files, the `FileProvider`, deep links, widgets and shortcuts;
- the Health Connect and Google Fit integration;
- dependencies, the Gradle build, APK signing and the GitHub Actions release workflow.

If the bug is really in a third-party library or in Android itself, please report it to them too —
and still let us know if it puts PULSE users at risk, because we may be able to work around it.

## Out of scope

Things we generally don't consider vulnerabilities:

- attacks that require a rooted phone or an unlocked bootloader;
- someone with physical access to an unlocked phone opening the app;
- data you chose to share yourself, such as an exported backup or a prompt you pasted into an AI
  tool;
- missing hardening that doesn't lead to an actual exploit (we're still happy to hear about it — just
  open a normal issue).

## Testing rules

Only test on devices, accounts and data that belong to you, or that you have permission to use. Don't
access or change anyone else's data, don't share modified builds of PULSE, and don't interfere with
our releases. If you come across someone else's personal data by accident, stop, don't keep a copy,
and tell us. This policy doesn't give you permission to test anything outside this project.

## Disclosure and credit

Please give us a chance to ship a fix before you publish details, so people have time to update.
We'll agree a disclosure date with you. If we go quiet for a long time, tell us you plan to publish —
we won't hold that against you.

We're happy to credit you in the advisory and release notes, under your name or a handle. We'll ask
first and never credit anyone without their permission.
