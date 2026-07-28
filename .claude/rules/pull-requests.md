---
description: Pull request title and description conventions
---

# Pull Request Guidelines

## Title

```
<Category>: <Short user-facing summary>
```

Titles appear in auto-generated changelogs and are read by users. Use ELI5, user-facing language —
no class names, library names, or implementation details. `refactor(settings): Migrate preferences
to DataStore` is the shape to avoid; `General: Remember settings between app restarts` is the shape
to use.

| Category | Covers |
|--------|--------|
| **Widget** | Home screen widget |
| **Reaction** | Case-open popup, auto-play/pause, notification triggers |
| **Device** | AirPods detection, compatibility, Bluetooth scanning, battery reading, device profiles |
| **General** | Dashboard, settings, notifications, themes, onboarding, support, app-wide UI |
| **Fix** | Bug fixes spanning multiple areas |

## Description

PRs are reviewed in GitHub's web UI, which already shows the file tree, the diff, and the tests.
The description answers what the diff can't. Use exactly these sections, in this order:

1. `## What changed`
2. `## Technical Context`
3. `## Review checklist` *(optional)*

No `Scope`, `Files changed`, `Tests`, or `Review guidance` sections.

**What changed** — user-facing explanation: the problem fixed or the feature added, from the user's
perspective. For refactors, tests, CI, and dependency bumps, write "No user-facing behavior change"
followed by a brief internal description.

**Technical Context** — one bullet per point, no prose paragraphs, no nested `**Bug 1**` headers.
Cover only what the diff can't show:
- **Why** this approach, and what was rejected
- **Root cause** for bug fixes — the diff shows the fix, not what caused it
- **Non-obvious side effects** or behavioral changes

**Review checklist** — `- [ ]` items, only when there are several non-trivial things to verify.
A single tricky point stays a Technical Context bullet.

## Conventions

- Link issues with "Closes #123" / "Fixes #123" / "Resolves #123"
- Prefix breaking changes with "BREAKING:"
- No `Co-authored-by` trailers
