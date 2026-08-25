---
description: Play Store screenshot pipeline — generation, copying, and adding or removing screens
paths:
  - "app/src/debug/**/screenshots/**"
  - "app/src/screenshotTest/**"
  - "fastlane/generate_screenshots.sh"
  - "fastlane/copy_screenshots.sh"
  - "fastlane/Fastfile"
  - "fastlane/metadata/android/*/images/phoneScreenshots/**"
---

# Play Store Screenshot Pipeline

## Overview

Localized screenshots are generated using Compose Preview Screenshot Testing (alpha), rendered offline (no device needed), and sorted into fastlane metadata directories for Play Store upload.

## Pipeline

```
ScreenshotContent.kt (mock data + composables)
    → PlayStoreScreenshots.kt (@PreviewTest entry points)
    → PlayStoreLocales.kt (multi-preview locale annotations, auto-generated per batch)
    → generate_screenshots.sh (batched Gradle runs to avoid OOM)
    → copy_screenshots.sh (sort PNGs into fastlane locale dirs)
    → fastlane/metadata/android/{locale}/images/phoneScreenshots/
```

## Key Files

| File | Purpose |
|------|---------|
| `app/src/debug/java/.../screenshots/ScreenshotContent.kt` | Mock data composables (7 exist; `HomescreenWidgetContent` has an IDE preview only and is **not** in the Play Store pipeline) |
| `app/src/screenshotTest/kotlin/.../screenshots/PlayStoreScreenshots.kt` | `@PreviewTest` functions (currently: `DashboardLight`, `DashboardDark`, `CasePopUp`, `DeviceProfiles`, `AddProfile`, `DeviceSettingsReactions`, `WidgetConfiguration`) |
| `app/src/screenshotTest/kotlin/.../screenshots/PlayStoreLocales.kt` | Multi-preview annotations. The committed content is an en-US placeholder, not meaningful data — `generate_screenshots.sh` rewrites it per batch and restores it from a `.bak` on exit. A run killed hard leaves that `.bak` behind, so the script now refuses to start until it is restored by hand |
| `fastlane/generate_screenshots.sh` | Batched generation; locale list (`ALL_LOCALES`) and `BATCH_SIZE` are defined inside the script |
| `fastlane/copy_screenshots.sh` | Copies rendered PNGs into fastlane structure |

## Commit policy

Only `en-US` has `phoneScreenshots/*.png` checked into the repo — 7 PNGs, ~1 MB tracked. Every other locale is excluded by `.gitignore`.

`--smoke` still *renders* 6 locales (en-US, de-DE, ja-JP, ar, zh-CN, pt-BR), but only en-US is committed. The other five cover LTR, RTL and CJK layout so a render that breaks on non-Latin script fails during generation, and the resulting PNGs sit in the working tree for manual inspection. Nothing compares them against a baseline, so this is render coverage plus eyeballing, not regression checking.

Play Store's `supply` only uploads what's present in `fastlane/metadata/android/<locale>/images/phoneScreenshots/`. For locales not in the upload, Play Store retains whatever was last pushed. So full localization on Play Store is maintained by an **occasional manual** full regen + `:screenshots_only` upload — not by every PR.

## Commands

```bash
# Default — smoke set (6 locales × 7 screens, ~42 PNGs, single batch).
# Use this for local iteration and PRs that touch screenshot content.
./fastlane/generate_screenshots.sh --smoke

# Full run — all 68 locales. Use only when intending to upload to Play Store
# (the non-smoke output is .gitignored and should not be committed).
./fastlane/generate_screenshots.sh

# Copy into fastlane directories (run after generate)
./fastlane/copy_screenshots.sh

# Clean copy (removes old screenshots first) — REQUIRED when screens are removed or renamed
./fastlane/copy_screenshots.sh --clean
```

## Adding a New Screenshot

1. Add a composable content function in `ScreenshotContent.kt` (e.g. `NewScreenContent()`)
2. Add a `@PreviewTest` function in `PlayStoreScreenshots.kt` that calls it
3. Add the function name → filename mapping in `copy_screenshots.sh` `SCREEN_MAP`
4. Update the expected count in `generate_screenshots.sh` (composables per locale)
5. Run the smoke pipeline: `generate_screenshots.sh --smoke` then `copy_screenshots.sh --clean`

## Removing or Renaming a Screenshot

1. Remove the `@PreviewTest` entry and its `SCREEN_MAP` mapping
2. Run `generate_screenshots.sh --smoke`
3. Run `copy_screenshots.sh --clean` — **`--clean` is required** here; without it, old files (e.g. a renamed `8_reaction_settings.png`) stay in `fastlane/metadata/android/<smoke locale>/images/phoneScreenshots/` and get uploaded to Play Store

## After UI Changes

When modifying a screen that appears in screenshots (check `ScreenshotContent.kt`), regenerate the smoke set:

```bash
./fastlane/generate_screenshots.sh --smoke
./fastlane/copy_screenshots.sh --clean
```

## Refreshing all locales on Play Store

Periodic, manual operation — not per-PR:

```bash
./fastlane/generate_screenshots.sh           # full, ~30 min, 476 PNGs (68 locales x 7)
./fastlane/copy_screenshots.sh --clean
git add fastlane/metadata/android/en-US/images/phoneScreenshots/
if bundle exec fastlane screenshots_only; then
  git checkout -- fastlane/metadata/android/ &&
    git commit --only -m "chore(screenshots): Refresh Play Store screenshots" -- \
      fastlane/metadata/android/en-US/images/phoneScreenshots/
else
  git checkout -- fastlane/metadata/android/
  echo "Upload failed; the refreshed en-US screenshots remain staged for retry."
fi
```

The `git add` has to happen before the upload. The final `git checkout` restores every tracked file under that path **from the index**, so staging the refreshed English set is precisely what makes it survive the checkout — skip the `git add` and the checkout silently reverts the refresh while the store still receives the new images.

Restoring is the checkout's job otherwise: `screenshots_only` runs `remove_unsupported_languages.sh`, which deletes 9 tracked locale directories (es-AR, sc-IT, sq-AL, uz, kmr-TR, ur-IN, zu, si-LK, nb) from the working tree before uploading — 35 tracked files, a subset of the 309 tracked non-screenshot metadata files under that path, all of them put back by the checkout. It does **not** touch the regenerated non-English PNGs: those are untracked and ignored, so they stay on disk and never show up in `git status`. Because the checkout discards any uncommitted metadata text edits too, run this refresh only with an otherwise-clean metadata tree. The final commit is path-limited on purpose, so an unrelated staged change can't ride along, and it is gated on `screenshots_only` succeeding rather than merely sequenced after it: if the upload fails, the refreshed English files stay staged for a retry instead of being committed as though they were deployed. The deleted locale directories are restored on either path.

## Technical Notes

- Batch size defaults to 2 locales; renders per batch = `BATCH_SIZE × screen count` (currently 2 × 7 = 14). Small batches avoid layoutlib memory leaks (~10MB/image)
- Gradle daemon is stopped between batches to release memory
- `PlayStoreLocales.kt` is temporarily rewritten per batch and restored via trap
- Device spec: 1080x2400px @ 428 DPI (Pixel-class phone)
- Uses `com.android.compose.screenshot` plugin v0.0.1-alpha13
- Output: `app/src/screenshotTestGplayDebug/reference/`
