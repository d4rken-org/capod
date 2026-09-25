#!/usr/bin/env bash
# Installs an app APK and an :app-e2e APK and runs every test class except UpgradeTest against it.
# Gradle's connected tasks only test debug builds, so this is how the R8-minified beta builds get
# tested. Wipes eu.darken.capod on the target device, which ANDROID_SERIAL must name.
#
# Usage: tools/device-test.sh <app.apk> <app-e2e.apk> <name>
# Results (instrumentation output, failure screenshots) land in app-e2e/build/outputs/beta-tests/<name>.
set -euo pipefail

if [ $# -ne 3 ]; then
    echo "Usage: $0 <app.apk> <app-e2e.apk> <name>" >&2
    exit 2
fi
APP_APK=$1
TEST_APK=$2
case "$3" in
    '' | *[!a-z0-9-]*) echo "The results name may only contain a-z, 0-9 and '-': $3" >&2; exit 2 ;;
esac
RESULTS=app-e2e/build/outputs/beta-tests/$3
: "${ANDROID_SERIAL:?set ANDROID_SERIAL to an emulator started for this run}"

APP=eu.darken.capod
TEST_PKG=eu.darken.capod.e2e
RUNNER=$TEST_PKG/androidx.test.runner.AndroidJUnitRunner
DEVICE_OUT=/sdcard/Android/media/$TEST_PKG/additional_test_output

# Debug builds share the release application id, so only a throwaway emulator may be wiped.
if [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]; then
    echo "$ANDROID_SERIAL is not an emulator, refusing to wipe $APP" >&2
    exit 2
fi

rm -rf "$RESULTS"
mkdir -p "$RESULTS"

# A differently signed build may be installed, so replace rather than update.
adb uninstall "$APP" >/dev/null 2>&1 || true
adb uninstall "$TEST_PKG" >/dev/null 2>&1 || true
adb install "$APP_APK"
adb install -t "$TEST_APK"
adb shell mkdir -p "$DEVICE_OUT"

adb shell am instrument -w \
    -e notClass eu.darken.capod.e2e.UpgradeTest \
    -e additionalTestOutputDir "$DEVICE_OUT" \
    "$RUNNER" | tee "$RESULTS/instrumentation.txt"

adb pull "$DEVICE_OUT/." "$RESULTS/" >/dev/null 2>&1 || echo "Could not pull $DEVICE_OUT" >&2
grep -qE '^OK \([1-9][0-9]* tests?\)' "$RESULTS/instrumentation.txt"
