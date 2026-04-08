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

PRs are reviewed in **GitHub's web UI**, which already shows the file tree, the diff, and the tests. Don't duplicate any
of it. The description should answer questions the diff can't — not restate it.

Only these sections, in this order:

1. `## What changed`
2. `## Technical Context`
3. `## Review checklist` *(optional)*

No `Scope`, `Files changed`, `Tests`, or `Review guidance` sub-sections — GitHub shows the files and tests, and review
notes belong in the checklist. Fold anything critical into a Technical Context bullet.

### What changed

User-friendly explanation of what this PR does. Describe the problem that was fixed or the feature that was added from the user's perspective. No internal class or method names.

For non-user-facing PRs (refactors, tests, CI, dependency bumps): write "No user-facing behavior change" followed by a brief internal description.

### Technical Context

Explain what's hard to extract from the diff alone. Focus on:

- **Why** this approach was chosen (and alternatives considered/rejected)
- **Root cause** for bug fixes (the diff shows the fix, not what caused it)
- **Non-obvious side effects** or behavioral changes not apparent from reading the code

Format rules:

- **One bullet per point.** No prose paragraphs, no nested sub-headers like `**Bug 1** / **Bug 2**` — if a PR fixes
  multiple bugs, one bullet per bug is enough.
- **Don't restate the diff.** File paths, class renames, test names, and line-level changes are all visible in the web
  UI.

### Review checklist (optional)

For PRs with multiple non-trivial review points, add a `## Review checklist` section with `- [ ]` tasks the reviewer can
tick off as they verify. Skip it for small PRs — a single tricky thing can stay as a Technical Context bullet.

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
