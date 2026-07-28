---
description: Sub-agent delegation limits and implementation scope for this project
---

# Agent Instructions

## Delegation

Delegation adds coordination overhead and multiplies token cost, so it has to earn its place through
genuine independence and parallel speedup.

- Delegate only for large, genuinely independent work that parallelizes — a wide multi-file
  investigation across unrelated areas, for example
- Don't delegate what you'd finish yourself in a handful of tool calls
- Don't spawn a sub-agent to verify or double-check your own work
- If one sub-agent can do it, use one rather than several
- Sub-agents don't inherit your conversation — state the full task, the relevant paths, and
  whether you want research only or research plus implementation
- `Explore` is the right type for read-only codebase investigation

Running Gradle through the build-runner agent is a separate standing rule in the user's global
CLAUDE.md; it is context isolation, not delegation, and this file does not restate it.

## Implementation scope

- Follow existing patterns — match the code style and architecture already in use
- Change only what the task needs
- When behavior is unexpected, fix the root cause rather than working around it
- Don't create new files when editing an existing one would do
- Don't refactor surrounding code while fixing a bug
- Don't add comments or docs to code you didn't change
- Don't guess at file paths — use Glob/Grep
