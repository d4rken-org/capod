# Release Process

Releases are cut via the **Release prepare** workflow (`.github/workflows/release-prepare.yml`). It bumps `version.properties` and `VERSION`, commits to `main`, tags `v<version>`, pushes atomically, and dispatches `release-tag.yml` which builds, signs, and uploads.

## Dispatch

```bash
# Plan only — no commit, no tag, no push.
gh workflow run release-prepare.yml -f bump_kind=build -f dry_run=true

# Real cut.
gh workflow run release-prepare.yml -f bump_kind=build -f dry_run=false
```

After `dry_run=false`: Job 1 computes + writes the summary, then Job 2 immediately commits/tags/pushes (no env gate — cancel the run between Job 1 and Job 2 if the summary looks wrong; you have ~seconds). The tag push naturally triggers `release-tag.yml` (the App-token push fires `on: push:` workflows; only `GITHUB_TOKEN`-pushes are suppressed). `release-tag.yml` then runs `validate-tag` and the existing `release-github` (`foss-production` approval) + `release-gplay` (`gplay-production` approval) jobs — those are the two human checkpoints, matching the pre-migration UX.

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

## Auth setup

`release-prepare.yml` Job 2 uses a GitHub App token (not `GITHUB_TOKEN`) to push the bump commit and tag. The App identity is in the rulesets' bypass list, which is what allows the push to bypass branch protection + tag-creation restrictions.

Required org secrets (set on the d4rken-org organization, accessible to `capod`):

- `RELEASE_APP_CLIENT_ID` — Client ID of the `d4rken-org-releaser` GitHub App (visible on the App's settings page, format `Iv1.<hex>` or similar)
- `RELEASE_APP_PRIVATE_KEY` — full `.pem` contents (including BEGIN/END lines)

The App is installed on this repo and added as a bypass actor to:
- The main-branch ruleset (PR + status check requirements)
- The tag ruleset (creation restriction on `v*`)

Other apps in the org can reuse the same App + secrets — just install the App on each repo and add it to that repo's rulesets' bypass lists.

## Defense in depth

`release-tag.yml` includes `validate-tag` which: (1) regex-checks `github.ref_name`, (2) runs `bump.sh --mode=check`, (3) asserts the parsed name matches the tag. Manual `gh workflow run release-tag.yml --ref vfoo` or hand-pushed tags fail before any build.

## Stuck-dispatch recovery

If Job 2's atomic push lands but the natural `on: push:` trigger doesn't fire `release-tag.yml` (rare — would mean GitHub dropped the event), the tag is public but no pipeline runs. Re-dispatch manually: `gh workflow run release-tag.yml --ref v<new> -f dry_run=false`.