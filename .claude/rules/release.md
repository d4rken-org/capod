# Release Process

Releases are cut via the **Release prepare** workflow (`.github/workflows/release-prepare.yml`). It bumps `version.properties` and `VERSION`, commits to `main`, tags `v<version>`, pushes atomically, and dispatches `release-tag.yml` which builds, signs, and uploads.

## Dispatch

```bash
# Plan only — no commit, no tag, no push.
gh workflow run release-prepare.yml -f bump_kind=build -f dry_run=true

# Real cut.
gh workflow run release-prepare.yml -f bump_kind=build -f dry_run=false
```

After `dry_run=false`: Job 1 computes + writes the summary, Job 2 pauses for `foss-production` environment approval, then commits/tags/pushes/dispatches. `release-tag.yml` then runs `validate-tag` and the existing `release-github` + `release-gplay` jobs (both with their own environment approvals).

## Inputs

| Input | Default | Notes |
|---|---|---|
| `bump_kind` | `build` | `build` \| `patch` \| `minor` \| `major` |
| `version_type` | `keep-current` | Preserves current `rc`/`beta`. Set explicitly to switch. |
| `version_override` | empty | e.g. `5.1.2-rc0`. Bypasses bump_kind/version_type. |
| `expected_current` | empty | Optional: fail if `version.properties` ≠ this. Useful for tight coordination. |
| `dry_run` | `true` | Default is plan-only. |

Bump rules: `build` increments build; `patch`/`minor`/`major` zero everything to the right of the bumped field. All numeric fields bounded `0..99` (the `versionCode` formula collapses at ≥100).

## Local

```bash
./tools/release/bump.sh --mode=plan --bump-kind=build --version-type=keep-current
./tools/release/bump.sh --mode=check
bats tools/release/bump.bats
```

## Channel mapping

| Tag suffix | FOSS APK | GitHub release | Fastlane lane | Play track | Rollout |
|---|---|---|---|---|---|
| `-beta*` | `assembleFossBeta` | pre-release | `beta` | `beta` | 10% |
| `-rc*` (or anything else) | `assembleFossRelease` | full release | `production` | **`beta`** | 10% |

`lane :production` in `Fastfile` uploads to Play's **beta** track at 10% — manually promoted to production via Play Console.

## Rollback

| Stage reached | Steps |
|---|---|
| Bump on `main`, downstream not started | `git push origin :refs/tags/v<bad>`, `git revert <bump-sha>`, push |
| GitHub release created | Above + `gh release delete v<bad> --yes --cleanup-tag` |
| Play upload completed | Above + halt rollout in Play Console (or `bundle exec fastlane supply --track beta --rollout 0 --version-code <bad-code>`) |
| Job 2 ran but downstream rejected at env approval | Treat as first row — bump+tag are public on `main` regardless of downstream outcome |

`bump.sh` enforces strict `versionCode` monotonicity, so re-using a code is impossible without manually editing `version.properties`.

## Defense in depth

`release-tag.yml` includes `validate-tag` which: (1) regex-checks `github.ref_name`, (2) runs `bump.sh --mode=check`, (3) asserts the parsed name matches the tag. Manual `gh workflow run release-tag.yml --ref vfoo` or hand-pushed tags fail before any build.

## Stuck-dispatch recovery

If Job 2's atomic push lands but `gh workflow run release-tag.yml` fails (rare — Job 1's auth precheck should prevent it), the tag is public but no pipeline runs. Re-dispatch: `gh workflow run release-tag.yml --ref v<new> -f dry_run=false`.