---
description: Gradle build, test, and lint commands, and what CI actually gates
---

# Build Commands

## Quick local check

```bash
./gradlew assembleFossDebug testFossDebugUnitTest
```

`assembleFossDebug` is the fastest variant — use it for iteration.

## What CI gates

`.github/workflows/code-checks.yml`, on every PR. Core Gradle gates:

```bash
# Lint vitals — flavor x variant matrix. Note: Beta/Release only, never Debug.
./gradlew lintVitalFossBeta lintVitalFossRelease lintVitalGplayBeta lintVitalGplayRelease

# Builds — Debug only
./gradlew app:assembleFossDebug app:assembleGplayDebug

# Unit tests — both flavors
./gradlew testFossDebugUnitTest testGplayDebugUnitTest
```

Four non-Gradle checks also run, **unconditionally** — there is no path filter, so they gate your PR
even if you didn't touch those areas:

```bash
bash fastlane/check_metadata_length.sh    # Play Store metadata length limits
shellcheck tools/release/bump.sh
bats tools/release/bump.bats
./tools/release/bump.sh --mode=check      # version.properties + VERSION consistency
```

Reproducing those locally is usually only worth it when you changed fastlane metadata or release
tooling, but a failure there blocks the PR regardless.

**Do not run `./gradlew check` as a pre-submit gate.** It runs the full non-vital `lint` task, which
is already failing on `main` for reasons unrelated to your change — you'll burn time chasing
pre-existing findings that CI never looks at. CI gates `lintVital*`, not `lint`.

## Other commands

```bash
./gradlew assembleGplayRelease            # release build
./gradlew bundleGplayRelease              # Play Store bundle
./gradlew connectedFossDebugAndroidTest   # instrumentation, needs a device/emulator
./gradlew lintFix                         # auto-fix where possible
./gradlew updateLintBaseline              # refresh the baseline
```
