# 1.8.1-beta1.8 — verification

PulseBottomSheet rebuilt on Material3 ModalBottomSheet (skipPartiallyExpanded, navigation-bar
padding, extra space under the last row) so no dropdown clips its last option (AI workout in
the New workout sheet included). Control stacks in Settings and Voice & audio use uniform
12 dp spacing matching the background-audio section. Checks: scripts/check_ui.py locally;
Gradle tests/build in CI on the PR and release tag.
