#!/usr/bin/env bash
# Installs an older CAPod build, sets up a user's state through the UI, upgrades that install in
# place to the current build, and checks the state survived (UpgradeTest in :app-e2e).
# Both app APKs are re-signed with this machine's debug key first, because an in-place install needs
# matching keys. Wipes eu.darken.capod on the target device, which ANDROID_SERIAL must name.
#
# Usage: tools/upgrade-test.sh <old-app.apk> <new-app.apk> <app-e2e.apk> [old-app-e2e.apk]
# The optional old-app-e2e.apk is the older build's own :app-e2e APK. beforeUpgrade then runs the
# steps written for that release; without it, the current test APK drives the older build.
# Results (instrumentation output, failure screenshots) land in app-e2e/build/outputs/upgrade-test.
set -euo pipefail

if [ $# -lt 3 ] || [ $# -gt 4 ]; then
    echo "Usage: $0 <old-app.apk> <new-app.apk> <app-e2e.apk> [old-app-e2e.apk]" >&2
    exit 2
fi
OLD_APK=$1
NEW_APK=$2
TEST_APK=$3
OLD_TEST_APK=${4:-}
: "${ANDROID_SERIAL:?set ANDROID_SERIAL to an emulator started for this run}"

APP=eu.darken.capod
TEST_PKG=eu.darken.capod.e2e
RUNNER=$TEST_PKG/androidx.test.runner.AndroidJUnitRunner
DEVICE_OUT=/sdcard/Android/media/$TEST_PKG/additional_test_output
RESULTS=app-e2e/build/outputs/upgrade-test

# Debug builds share the release application id, so only a throwaway emulator may be wiped.
if [ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]; then
    echo "$ANDROID_SERIAL is not an emulator, refusing to wipe $APP" >&2
    exit 2
fi

run_phase() {
    echo "== UpgradeTest#$1"
    adb shell am instrument -w \
        -e class "eu.darken.capod.e2e.UpgradeTest#$1" \
        -e additionalTestOutputDir "$DEVICE_OUT" \
        "$RUNNER" | tee "$RESULTS/$1.txt"
    grep -q '^OK (1 test)' "$RESULTS/$1.txt"
}

prepare_output() {
    adb shell rm -rf "$DEVICE_OUT"
    adb shell mkdir -p "$DEVICE_OUT"
}

# Uninstalling the test package deletes this directory, so pull before every swap.
collect_output() {
    adb pull "$DEVICE_OUT/." "$RESULTS/" >/dev/null 2>&1 || echo "Could not pull $DEVICE_OUT" >&2
}

BUILD_TOOLS=$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:?set ANDROID_HOME}}"/build-tools/*/ | sort -V | tail -1)
DEBUG_KEYSTORE=${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

certificate() {
    "$BUILD_TOOLS/apksigner" verify --print-certs "$1" | grep -m1 'SHA-256 digest' | sed 's/.*: //'
}

# Prints the key each APK was built with, so a build that signs unexpectedly shows up in the log.
resign() {
    echo "$2 APK built with key $(certificate "$1")"
    cp "$1" "$WORK/$2.apk"
    "$BUILD_TOOLS/apksigner" sign --ks "$DEBUG_KEYSTORE" --ks-pass pass:android \
        --ks-key-alias androiddebugkey --key-pass pass:android "$WORK/$2.apk"
}

resign "$OLD_APK" old
resign "$NEW_APK" new
echo "Both re-signed with key $(certificate "$WORK/new.apk")"

rm -rf "$RESULTS"
mkdir -p "$RESULTS"

adb uninstall "$APP" >/dev/null 2>&1 || true
adb uninstall "$TEST_PKG" >/dev/null 2>&1 || true
adb install "$WORK/old.apk"
if [ -n "$OLD_TEST_APK" ]; then
    echo "beforeUpgrade runs from the older build's test APK"
    adb install -t "$OLD_TEST_APK"
else
    echo "beforeUpgrade runs from the current test APK"
    adb install -t "$TEST_APK"
fi
prepare_output

result=0
if run_phase beforeUpgrade; then
    collect_output
    # The two test APKs can carry different keys, so swap them by uninstalling.
    adb uninstall "$TEST_PKG" >/dev/null
    adb install -t "$TEST_APK"
    prepare_output
    adb install -r "$WORK/new.apk"
    run_phase afterUpgrade || result=1
else
    result=1
fi

collect_output
if [ "$result" -ne 0 ]; then
    echo "Upgrade test failed, see $RESULTS" >&2
fi
exit "$result"
