# Security Policy

We take the security of PULSE and the privacy of workout and health data seriously.

## Supported Versions

Security fixes target the latest stable release. Older releases may not receive backported fixes.

When possible, verify whether an issue affects the latest release, but do not delay reporting a critical vulnerability.

## Reporting a Vulnerability

Report suspected security vulnerabilities privately to:

**security@nexalab.fr**

Do not disclose unfixed vulnerabilities through public GitHub issues, pull requests, or discussions.

### Very Critical or Actively Exploited Issues

For very critical vulnerabilities or evidence of active exploitation, contact **security@nexalab.fr immediately**.

Use this subject format:

`[CRITICAL] PULSE — Short description`

Examples include:
- Remote code execution.
- Unauthorized access to private files or sensitive health data.
- Bypasses of security or permission boundaries.
- Compromise of signing keys, published APKs, or the release process.

Describe the potential impact and any evidence of exploitation. Do not send compromised secrets or sensitive user data by email.

## What to Include

A useful report contains:

- Affected PULSE version or commit.
- Android version and relevant device information.
- A description of the vulnerability and affected component.
- Reproduction steps or a minimal proof of concept.
- Expected behavior and observed behavior.
- Potential impact and any required permissions or prerequisites.
- Redacted logs or screenshots, if helpful.

Use synthetic data where possible. Remove personal health information,
private workout history, access tokens, signing material, and other secrets
from attachments. Do not submit an unredacted personal backup.

## Scope

Relevant areas include:

- Workout JSON and backup import/export.
- Local storage and access to private files.
- Media handling, shared files, deep links, and Android permissions.
- Health Connect and Google Fit integrations.
- Dependencies, build configuration, APK signing, and release workflows.

Issues affecting third-party products should also be reported to their
maintainers. If they expose PULSE users to a vulnerability, explain that
impact in your report.

## Responsible Testing

- Test only devices, accounts, systems, and data you own or are authorized to test.
- Use the minimum access necessary to demonstrate the issue.
- Do not access, alter, or disclose another person's data.
- Do not disrupt services, distribute malicious builds, or interfere with releases.
- Stop testing and notify us if you encounter sensitive information.

This policy does not authorize testing against third-party systems.

## Coordinated Disclosure

Please coordinate public disclosure with us so affected users have an
opportunity to install a fix before exploit details are published.

We may request additional information to assess a report. Security fixes
and relevant guidance will be communicated through release notes or security
advisories, as appropriate.

Researcher credit will only be published with the reporter's consent.
