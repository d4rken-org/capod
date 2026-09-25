---
description: Emulator tests in :app-e2e — what they cover, how to run them, the upgrade test, and the CI workflow
paths:
  - "app-e2e/**"
  - "tools/upgrade-test.sh"
  - "tools/device-test.sh"
  - ".github/workflows/emulator.yml"
---

# Device Tests

`:app-e2e` is a `com.android.test` module that drives the installed app with UiAutomator, the way a
user does. It is self-instrumenting: the tests run in their own process, so they can `pm clear`,
force-stop and relaunch the app. Its sources live in `src/main` (both flavors), `src/foss` and
`src/gplay`, not in `androidTest`. Selectors look up the app's string resources by name through
`CapodApp.text(...)`/`desc(...)`, never English literals. Pod model names are the exception: they
are code constants, not resources.

| Test | Flavor | Covers |
|---|---|---|
| `GoldenPathTest` | both | Onboarding, granting the dashboard's permissions through its cards and the system dialog, relaunch skips onboarding |
| `SponsorPitchTest` | foss | The sponsor pitch is reachable from the dashboard, a locked setting and the upgrade status |
| `UpgradeScreenTest` | gplay | Without a Play Store the upgrade screen reports Play as unavailable and settles on "prices unavailable" |
| `UpgradeTest` | foss | State set up in an older release survives an in-place upgrade |

An emulator never receives AirPods advertisements and cannot open an AAP session, so nothing that
needs a device card, a reaction or the monitor service belongs here. A fresh install gets a profile
without a paired device, which keeps background monitoring off; tests rely on that to reach a stable
dashboard.

`ANDROID_SERIAL` is mandatory: without it `connectedAndroidTest` installs and runs on every attached
device, and the tests start with `pm clear eu.darken.capod`, which debug builds share with the release
application id. Gradle also uninstalls the app when the run ends, so its data is lost on any device
the run reaches. Point it only at an emulator started for the run.

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app-e2e:connectedFossDebugAndroidTest
ANDROID_SERIAL=emulator-5554 ./gradlew :app-e2e:connectedGplayDebugAndroidTest
```

## Minified builds

Users get R8-minified beta and release builds, but the Gradle tasks only test debug builds.
`tools/device-test.sh` installs a given app APK in place of any installed build and runs every class
except `UpgradeTest` against it. Beta builds
sign with the keystore that `STORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` name, so a
throwaway keystore is enough:

```bash
keytool -genkeypair -keystore /tmp/e2e.keystore -storepass android -keypass android -alias e2e \
    -keyalg RSA -keysize 2048 -validity 365 -dname "CN=CAPod e2e"
STORE_PATH=/tmp/e2e.keystore STORE_PASSWORD=android KEY_ALIAS=e2e KEY_PASSWORD=android \
    ./gradlew :app:assembleFossBeta :app-e2e:assembleFossDebug -x lintVitalFossBeta
ANDROID_SERIAL=emulator-5554 tools/device-test.sh app/build/outputs/apk/foss/beta/app-foss-beta.apk \
    app-e2e/build/outputs/apk/foss/debug/app-e2e-foss-debug.apk foss
```

## Upgrade test

`UpgradeTest.beforeUpgrade` onboards an older build, grants permissions, renames the default profile
and sets its model, becomes a supporter through the real sponsor flow (the browser opens, the app
comes back after `SPONSOR_DELAY_MS`), and switches to the dark theme. It then force-stops and
relaunches to prove that build saved all of it. `afterUpgrade` expects the same state after the
current build is installed over it. The Gradle task skips the class, because only
`tools/upgrade-test.sh` swaps the APK between the phases. When the baseline release already contains
`UpgradeTest`, CI pairs that release's `beforeUpgrade` with the current `afterUpgrade`, so a new setup
step and its check land one release apart.

The script re-signs both app APKs with a throwaway key, since an in-place install needs matching
keys. An optional fourth argument is the older build's own `:app-e2e` APK, so
`beforeUpgrade` runs the steps written for that release; without it (tags older than `UpgradeTest`),
the current test APK drives the older build. Results land in `app-e2e/build/outputs/upgrade-test`.

CI runs it between beta builds. Locally, first create the throwaway keystore shown above:

```bash
export STORE_PATH=/tmp/e2e.keystore STORE_PASSWORD=android KEY_ALIAS=e2e KEY_PASSWORD=android
git worktree add --detach /tmp/upgrade-base "$(git describe --tags --abbrev=0 --match 'v*' HEAD^)"
(cd /tmp/upgrade-base && ./gradlew :app:assembleFossBeta -x lintVitalFossBeta)
./gradlew :app:assembleFossBeta :app-e2e:assembleFossDebug -x lintVitalFossBeta
ANDROID_SERIAL=emulator-5554 tools/upgrade-test.sh \
    /tmp/upgrade-base/app/build/outputs/apk/foss/beta/app-foss-beta.apk \
    app/build/outputs/apk/foss/beta/app-foss-beta.apk \
    app-e2e/build/outputs/apk/foss/debug/app-e2e-foss-debug.apk
```

## CI

The `Emulator tests` workflow (`.github/workflows/emulator.yml`) runs the FOSS debug tests on API 30
and API 36. API 36 additionally runs the GPlay debug tests, both flavors' beta builds through
`tools/device-test.sh`, and the upgrade test between the beta builds of the nearest `v*` tag before
`HEAD` and the current code. The `device-tests-api-<level>` artifact, uploaded even when tests fail,
holds the reports plus a screenshot and window dump of every failed test.
