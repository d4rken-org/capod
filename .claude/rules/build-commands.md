---
description: Build, test, lint, and release commands for Gradle
globs:
  - "**/*.gradle.kts"
  - "**/*.gradle"
  - "gradle/**"
---

# Build Commands

## Build

```bash
# Build debug version
./gradlew assembleDebug

# Build all variants (FOSS and Google Play flavors)
./gradlew assemble

# Build specific flavor and type
./gradlew assembleFossDebug
./gradlew assembleGplayRelease

# Build app bundles for Play Store
./gradlew bundleGplayRelease
```

## Testing

```bash
# Run all unit tests
./gradlew test

# Run unit tests for specific variant
./gradlew testFossDebugUnitTest

# Run instrumentation tests (requires connected device/emulator)
./gradlew connectedAndroidTest
./gradlew connectedFossDebugAndroidTest

# Run all checks (lint + tests)
./gradlew check
```

## Code Quality

```bash
# Run lint for all variants
./gradlew lint

# Run lint for specific variant
./gradlew lintFossDebug

# Auto-fix lint issues where possible
./gradlew lintFix

# Update lint baseline
./gradlew updateLintBaseline
```

## Release

```bash
./gradlew assembleFossRelease assembleGplayRelease
```

## Notes

- Use `assembleFossDebug` as the default quick-check build (fastest variant)
- Run `./gradlew check` before submitting changes to catch lint and test issues
- Instrumentation tests require a connected device or running emulator
