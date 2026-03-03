---
description: Pull request naming and description conventions
globs:
  - "**"
---

# Pull Request Guidelines

## PR Title Format

```
<Category>: <Short user-facing summary>
```

PR titles appear in auto-generated changelogs and are read by users. Use **ELI5, user-facing language** — no internal class names, library names, or implementation details.

## Category Prefixes

| Prefix | Covers |
|--------|--------|
| **Widget** | Home screen widget |
| **Reaction** | Case-open popup, auto-play/pause, notification triggers |
| **Device** | AirPods detection, compatibility, Bluetooth scanning, battery reading, device profiles |
| **General** | Dashboard, settings, notifications, themes, onboarding, support, app-wide UI |
| **Fix** | Bug fixes spanning multiple areas |

For non-user-facing PRs (refactors, tests, CI, dependency bumps, translations), apply the `changelog-ignore` GitHub label. These don't need user-facing titles.

### Title Examples

- `Widget: Add color themes and transparency slider`
- `Reaction: Fix popup appearing twice when opening AirPods case`
- `Device: Add support for AirPods 4 with ANC`
- `General: Add dark mode and color theme settings`
- `Fix: Fix battery display stuck at 0% after reconnecting`

### Bad Titles (too technical)

- `refactor(settings): Migrate preferences to AndroidX DataStore`
- `feat(widget): Migrate to Jetpack Glance`
- `refactor(ui): Migrate from Fragments to Jetpack Compose`

## PR Description Format

### What changed

User-friendly explanation of what this PR does. Describe the problem that was fixed or the feature that was added from the user's perspective. No internal class or method names.

For non-user-facing PRs (refactors, tests, CI, dependency bumps): write "No user-facing behavior change" followed by a brief internal description.

### Technical Context

Explain what's hard to extract from the diff alone. Focus on:

- **Why** this approach was chosen (and alternatives considered/rejected)
- **Root cause** for bug fixes (the diff shows the fix, not what caused it)
- **Non-obvious side effects** or behavioral changes not apparent from reading the code
- **Review guidance** — what's tricky or deserves close attention

Keep it scannable with bullet points. Don't restate what's visible in the diff (file names, class renames, line-level changes).

### Example

```markdown
## What changed

Fixed a crash that could happen when the AirPods case is opened while Bluetooth is turning off.

## Technical Context

- Root cause: `MonitorService` continued processing scan results during Bluetooth adapter state change, hitting a null adapter reference
- Chose to gate on adapter state in the scan callback rather than adding a separate BroadcastReceiver, since the service already observes adapter state for restart logic
- The timing window is ~200ms between ACTION_STATE_CHANGING and ACTION_STATE_OFF — only reproducible on Pixel devices with aggressive Bluetooth power management
```

## Conventions

- **Issue references**: Use "Closes #123", "Fixes #123", or "Resolves #123"
- **Breaking changes**: Mark with "BREAKING:" prefix if applicable
- **No `Co-authored-by` trailers** (per project convention)
