---
description: Release guardrails — what never to do by hand. Full procedure is the /release skill.
---

# Release

The procedure lives in the `/release` skill (invoke it deliberately; it does not auto-load).
These constraints apply regardless of whether that skill was invoked.

- **`release-prepare.yml` is the only sanctioned path** for bumping a version or creating a release
  tag. Do not edit `version.properties` or `VERSION` by hand, and do not create `v*` tags manually —
  `validate-tag` in `release-tag.yml` rejects anything not matching `v<M.m.p>-(rc|beta)N`.
- **`tools/release/bump.sh` is the single source of truth for version logic.** Its versionCode
  formula mirrors `buildSrc/src/main/java/ProjectConfig.kt` — the two must stay in sync.
- CI runs `./tools/release/bump.sh --mode=check` on every PR (`check-release-tooling` in
  `code-checks.yml`). If you did touch `version.properties` or `VERSION`, run that locally first.
- All numeric version fields are bounded `0..99`; the versionCode formula collapses at ≥100.
