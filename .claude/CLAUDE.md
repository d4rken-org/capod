# CAPod - Companion for AirPods

Android app that detects and monitors AirPods via Bluetooth LE. Displays battery levels, triggers popup notifications on case open, and provides home screen widgets.

## Project Structure

| Module | Description |
|--------|-------------|
| `app/` | Main Android app (FOSS and Google Play flavors) |
| `app-common/` | Shared code between phone and Wear OS apps |

## Build Flavors

- **FOSS** (`foss`): Open-source, no Google Play dependencies
- **Google Play** (`gplay`): Includes billing client for IAP

Quick build check: `./gradlew assembleFossDebug`

## Key Locations

| Path | Contains |
|------|----------|
| `app/src/main/java/` | Main app source (activities, fragments, services) |
| `app-common/src/main/java/` | Shared logic (monitor, bluetooth, models) |
| `app/src/main/res/` | Layouts, drawables, strings |
| `app-common/src/test/` | Unit tests |
| `app/build.gradle.kts` | App build config, dependencies, flavors |

## Development Tips

- Use `assembleFossDebug` as the fastest build variant for iteration
- Shared code goes in `app-common/`, app-specific code in `app/`
- Follow existing patterns — the codebase uses MVVM + Hilt + Coroutines
- Always use string resources for user-facing text (see localization rules)
- Check `git log --oneline -20` for commit message style before committing

## Rules Reference

Detailed guidelines are in `.claude/rules/`:

- `architecture.md` — Module structure, key components, data flow, dependencies
- `build-commands.md` — Build, test, lint, and release commands
- `localization.md` — String resource naming conventions
- `commit-guidelines.md` — Commit message format and prefixes
- `agent-instructions.md` — Sub-agent delegation and critical thinking
