---
description: Instructions for Claude Code sub-agents and task delegation
globs:
  - "**"
---

# Agent Instructions

## Critical Thinking

- Do not blindly accept information at face value
- Verify assumptions against actual code before proceeding
- When encountering unexpected behavior, investigate root causes rather than applying workarounds
- If something seems wrong, it probably is — dig deeper

## Explore vs. Implement

- **Explore first**: Before making changes, understand the existing code structure and patterns
- **Read before writing**: Always read relevant files before modifying them
- **Follow existing patterns**: Match the code style and architecture already in use
- **Minimal changes**: Only change what's necessary to accomplish the task

## Sub-Agent Delegation

When using Task tool to spawn sub-agents:

- Provide complete context — sub-agents don't share your conversation history unless noted
- Be specific about what you need: research only, or research + implementation
- Use `Explore` agent type for codebase investigation
- Use `Bash` agent type for running builds and tests
- Parallelize independent sub-agent tasks for efficiency

## Common Pitfalls

- Don't create new files when editing existing ones would suffice
- Don't add features beyond what was requested
- Don't refactor surrounding code when fixing a bug
- Don't add comments or documentation to code you didn't change
- Don't guess at file paths — use Glob/Grep to find them
