# CAPod - Companion for AirPods

Android app that detects and monitors AirPods via Bluetooth LE. Displays battery levels, triggers popup notifications on case open, and provides home screen widgets.

## Project Structure

Single Gradle module `app/` with multiple source sets (`main`, `foss`, `gplay`, `debug`, `test`, `testFoss`, `testGplay`, `screenshotTest`). A previous `app-common/` module was merged into `app/`.

## Build Flavors

- **FOSS** (`foss`): Open-source, no Google Play dependencies
- **Google Play** (`gplay`): Includes billing client for IAP

Quick build check: `./gradlew assembleFossDebug`

## Key Locations

| Path | Contains |
|------|----------|
| `app/src/main/java/` | Main app source (Compose screens, services, receivers, monitor, bluetooth, models) |
| `app/src/foss/java/`, `app/src/gplay/java/` | Flavor-specific code (e.g. upgrade/billing) |
| `app/src/main/res/` | Layouts, drawables, strings |
| `app/src/test/`, `app/src/testFoss/`, `app/src/testGplay/` | Unit tests (shared + flavor-specific) |
| `app/build.gradle.kts` | App build config, dependencies, flavors |
| `app/src/debug/java/.../screenshots/` | Play Store screenshot content composables |
| `fastlane/` | Screenshot generation scripts, Play Store metadata |

## Development Tips

- Use `assembleFossDebug` as the fastest build variant for iteration
- Follow existing patterns — the codebase uses MVVM + Hilt + Coroutines
- Always use string resources for user-facing text
- Check `git log --oneline -20` for commit message style before committing
- Ordinary unit tests use JUnit 5 + kotest assertions + mockk and extend `testhelpers.BaseTest` — not
  the Android defaults. `testFossDebugUnitTest` does not run `testGplay` tests
- Changing a production screen that backs a `@PreviewTest` entry in `PlayStoreScreenshots.kt` means
  regenerating the smoke screenshot set

## Rules Reference

Always loaded:

| Rule | Covers |
|------|--------|
| `.claude/rules/architecture.md` | BLE vs AAP paths, `DeviceMonitor` merge boundary, FOSS pro gating |
| `.claude/rules/build-commands.md` | Gradle commands and what CI actually gates |
| `.claude/rules/commit-guidelines.md` | Commit message format and prefixes |
| `.claude/rules/pull-requests.md` | PR title and description conventions |
| `.claude/rules/agent-instructions.md` | Delegation limits and implementation scope |
| `.claude/rules/release.md` | Release guardrails — never hand-edit versions or tags |

Loaded on demand, when a matching file is read (`paths:` frontmatter):

| Rule | Loads for |
|------|-----------|
| `.claude/rules/testing.md` | `app/src/test/`, `testFoss/`, `testGplay/` |
| `.claude/rules/localization.md` | `**/res/values/strings.xml` (base locale) |
| `.claude/rules/screenshots.md` | Screenshot composables, `screenshotTest/`, fastlane scripts |

Skills, invoked by name:

| Skill | Purpose |
|-------|---------|
| `/release` | Release workflow dispatch, inputs, channel mapping, rollback |
