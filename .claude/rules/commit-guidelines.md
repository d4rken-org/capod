---
description: Git commit message format and conventions
globs:
  - "**"
---

# Commit Guidelines

## Format

```
<prefix>: <Short summary>
```

Summary line should be concise and describe the change. No period at the end.

## Prefixes

Use the existing commit history as reference. Common prefixes:

- **fix**: Bug fixes (e.g., `fix: Handle display cutouts in landscape mode`)
- **feat**: New features
- **refactor**: Code restructuring without behavior change
- **chore**: Maintenance, dependency updates, build config
- **docs**: Documentation changes

## Component Scope (optional)

When a change is scoped to a specific area, include it after the prefix:

- `fix(widget): Fix layout for devices with single charge detection`
- `feat(monitor): Add battery level caching`
- `refactor(popup): Extract pod view factory`

## Rules

- Keep the summary line under 72 characters
- Use imperative mood ("Add feature" not "Added feature")
- Reference issue numbers when applicable
- Do not include `Co-authored-by` trailers
- Look at recent `git log` output to match the project's existing style
