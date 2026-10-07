#!/usr/bin/env bash
#
# Capture the screenshots the build environment could not produce.
#
# There is no emulator or physical device where this project was assembled, so no screenshots of the
# running app exist in the repository. This script takes them on a machine that does have a device.
#
# Usage:
#   scripts/capture-screenshots.sh                 # first connected device
#   scripts/capture-screenshots.sh emulator-5554   # a specific one
#
# Prerequisites: adb on PATH, and the app installed:
#   ./gradlew :app:assembleDebug
#   adb install -r app/build/outputs/apk/debug/app-debug.apk
#
# The navigation taps assume a 1080x2400 phone; adjust the coordinates for other screens, or drive
# the app by hand and capture single frames with:
#   adb exec-out screencap -p > docs/screenshots/03-template-gallery.png

set -euo pipefail

SERIAL="${1:-}"
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/docs/screenshots"
PKG="com.pulse.intervalcoach.debug"
ACTIVITY="$PKG/com.pulse.intervalcoach.MainActivity"
SETTLE="${SETTLE:-3}"

if [[ -n "$SERIAL" ]]; then
  adb() { command adb -s "$SERIAL" "$@"; }
fi

mkdir -p "$OUT_DIR"

shot() {
  local name="$1"
  sleep "$SETTLE"
  command adb ${SERIAL:+-s "$SERIAL"} exec-out screencap -p > "$OUT_DIR/$name.png"
  echo "captured $name.png"
}

tap() {
  local x="$1" y="$2"
  command adb ${SERIAL:+-s "$SERIAL"} shell input tap "$x" "$y"
}

echo "device: $(command adb ${SERIAL:+-s "$SERIAL"} get-state)"
command adb ${SERIAL:+-s "$SERIAL"} shell am force-stop "$PKG" || true
command adb ${SERIAL:+-s "$SERIAL"} shell am start -n "$ACTIVITY" > /dev/null
sleep 4

shot 01-home

# Bottom navigation: Workouts / Progress / Settings tabs.
tap 400 2280 ; shot 02-workouts
tap 800 2280 ; shot 09-progress
tap 1000 2280 ; shot 11-settings

echo
echo "Screenshots written to $OUT_DIR."
echo "Screens that need in-app navigation (builders, player, summary, voice studio, templates) are"
echo "easiest to capture by hand — open the screen, then:"
echo "  adb exec-out screencap -p > docs/screenshots/05-advanced-builder.png"
