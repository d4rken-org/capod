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
- Always use string resources for user-facing text (see localization rules)
- Check `git log --oneline -20` for commit message style before committing

## Rules Reference

Detailed guidelines are in `.claude/rules/`:

- `architecture.md` — Module structure, key components, data flow, dependencies
- `build-commands.md` — Build, test, lint, and release commands
- `localization.md` — String resource naming conventions
- `commit-guidelines.md` — Commit message format and prefixes
- `pull-requests.md` — PR title and description conventions
- `agent-instructions.md` — Sub-agent delegation and critical thinking
- `screenshots.md` — Play Store screenshot pipeline, commands, adding new screens
