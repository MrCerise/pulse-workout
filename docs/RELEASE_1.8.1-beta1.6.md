# 1.8.1-beta1.6 — verification

Changes: centred-header overlap fix, shared drop-down style for Language and Category,
package renamed to `com.mrcerise.intervalcoach`, display name "Interval Coach", faster
duration editing (±5 s, ±1 min, tap-to-type min:sec), inline big name boxes, Run/Walk folded
into Running, GPS route recorded and drawn on the summary, session detail and share image,
spacing between the audio-interruption and headphone rows, history search/status/period
filters, sessions under one minute not stored, distinct Today stats, and QR-code workout
sharing with the app logo in the centre.

Checks run: `scripts/check_ui.py`, `scripts/check_brand.py`, `scripts/kotlin_xref_gate.py`,
`scripts/contrast_check.py` (CONTRAST.md regenerated). No Java/Android SDK in the sandbox, so
the Gradle build and unit tests run in CI on the PR / release tag.
Note: the package rename means Android installs this as a new app (data does not migrate
from `com.pulse.intervalcoach` installs).
