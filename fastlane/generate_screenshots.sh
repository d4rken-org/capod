#!/usr/bin/env bash
set -euo pipefail

# Generates localized Play Store screenshots in batches to work around
# layoutlib ImagePoolImpl memory leak (accumulates rendered images without release).
#
# Usage:
#   ./fastlane/generate_screenshots.sh          # Full run (all locales, ~12 batches)
#   ./fastlane/generate_screenshots.sh --smoke   # Smoke test (6 locales, 1 batch)
#   ./fastlane/generate_screenshots.sh --batch-size 10  # Custom batch size

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOCALES_FILE="$PROJECT_DIR/app/src/screenshotTest/kotlin/eu/darken/capod/screenshots/PlayStoreLocales.kt"
REF_DIR="$PROJECT_DIR/app/src/screenshotTestGplayDebug/reference"

# Default batch size — 2 locales × 8 composables = 16 renders per batch.
# Kept small to avoid layoutlib OOM (leaks ~10MB per rendered image at 1080p).
BATCH_SIZE=2
SMOKE=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --smoke) SMOKE=true; shift ;;
        --batch-size) BATCH_SIZE="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# Each entry: "android_locale:fastlane_name"
ALL_LOCALES=(
    "en:en-US"
    "af:af"
    "am:am"
    "ar:ar"
    "az:az-AZ"
    "be:be"
    "bg:bg"
    "bn-BD:bn-BD"
    "ca:ca"
    "cs:cs-CZ"
    "da:da-DK"
    "de:de-DE"
    "el:el-GR"
    "es:es-ES"
    "es-MX:es-419"
    "et-EE:et"
    "eu-ES:eu-ES"
    "fa:fa"
    "fi:fi-FI"
    "fil:fil"
    "fr:fr-FR"
    "gl-ES:gl-ES"
    "hi-IN:hi-IN"
    "hr:hr"
    "hu:hu-HU"
    "hy-AM:hy-AM"
    "in:id"
    "is:is-IS"
    "it:it-IT"
    "iw:iw-IL"
    "ja:ja-JP"
    "ka-GE:ka-GE"
    "km-KH:km-KH"
    "kn-IN:kn-IN"
    "ko:ko-KR"
    "ky-KG:ky-KG"
    "lo-LA:lo-LA"
    "lt:lt"
    "lv:lv"
    "mk-MK:mk-MK"
    "ml-IN:ml-IN"
    "mn-MN:mn-MN"
    "mr-IN:mr-IN"
    "ms:ms"
    "my-MM:my-MM"
    "nb:no-NO"
    "ne-NP:ne-NP"
    "nl:nl-NL"
    "pl:pl-PL"
    "pt:pt-PT"
    "pt-BR:pt-BR"
    "rm:rm"
    "ro:ro"
    "ru:ru-RU"
    "sk:sk"
    "sl:sl"
    "sr:sr"
    "sv:sv-SE"
    "sw:sw"
    "ta-IN:ta-IN"
    "te-IN:te-IN"
    "th:th"
    "tr:tr-TR"
    "uk:uk"
    "vi:vi"
    "zh-CN:zh-CN"
    "zh-HK:zh-HK"
    "zh-TW:zh-TW"
)

SMOKE_LOCALES=(
    "en:en-US"
    "de:de-DE"
    "ja:ja-JP"
    "ar:ar"
    "zh-CN:zh-CN"
    "pt-BR:pt-BR"
)

if $SMOKE; then
    LOCALES=("${SMOKE_LOCALES[@]}")
    BATCH_SIZE=${#LOCALES[@]}  # Single batch for smoke
else
    LOCALES=("${ALL_LOCALES[@]}")
fi

TOTAL=${#LOCALES[@]}
NUM_BATCHES=$(( (TOTAL + BATCH_SIZE - 1) / BATCH_SIZE ))

echo "=== Localized Screenshot Generation ==="
echo "Locales: $TOTAL | Batch size: $BATCH_SIZE | Batches: $NUM_BATCHES"
echo ""

# Back up the original file
cp "$LOCALES_FILE" "$LOCALES_FILE.bak"
trap 'mv "$LOCALES_FILE.bak" "$LOCALES_FILE"; echo "Restored original PlayStoreLocales.kt"' EXIT

# Clean reference directory from previous runs
rm -rf "$REF_DIR"
echo "Cleaned reference directory"

generate_locales_file() {
    local -n batch_locales=$1
    local file="$2"

    cat > "$file" << 'HEADER'
package eu.darken.capod.screenshots

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Multi-preview annotation generating one preview per Play Store-supported locale (light mode).
 * Each [name] is the fastlane metadata directory name for direct use in the copy script.
 */
HEADER

    # Light annotations
    for entry in "${batch_locales[@]}"; do
        local locale="${entry%%:*}"
        local name="${entry##*:}"
        echo "@Preview(locale = \"$locale\", name = \"$name\", device = DS)" >> "$file"
    done
    echo "annotation class PlayStoreLocales" >> "$file"
    echo "" >> "$file"

    # Dark annotations
    echo "/**" >> "$file"
    echo " * Same locales but with night mode enabled for dark theme screenshots." >> "$file"
    echo " */" >> "$file"
    for entry in "${batch_locales[@]}"; do
        local locale="${entry%%:*}"
        local name="${entry##*:}"
        echo "@Preview(locale = \"$locale\", name = \"$name\", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)" >> "$file"
    done
    echo "annotation class PlayStoreLocalesDark" >> "$file"
    echo "" >> "$file"

    # Smoke annotation (single entry placeholder)
    echo "/**" >> "$file"
    echo " * Smoke test subset for fast iteration (6 locales covering LTR, RTL, CJK)." >> "$file"
    echo " */" >> "$file"
    echo "@Preview(locale = \"en\", name = \"en-US\", device = DS)" >> "$file"
    echo "annotation class PlayStoreLocalesSmoke" >> "$file"
}

for (( batch=0; batch < NUM_BATCHES; batch++ )); do
    start=$(( batch * BATCH_SIZE ))
    end=$(( start + BATCH_SIZE ))
    if (( end > TOTAL )); then
        end=$TOTAL
    fi

    # Extract batch slice
    BATCH_SLICE=("${LOCALES[@]:$start:$((end - start))}")
    batch_num=$(( batch + 1 ))

    echo "--- Batch $batch_num/$NUM_BATCHES (locales $((start+1))-$end of $TOTAL) ---"

    # Generate locales file for this batch
    generate_locales_file BATCH_SLICE "$LOCALES_FILE"

    # Stop daemon to release memory from previous batch
    echo "Stopping Gradle daemon..."
    cd "$PROJECT_DIR"
    ./gradlew --stop 2>/dev/null || true

    # Run screenshot generation
    echo "Generating screenshots..."
    if ! ./gradlew updateGplayDebugScreenshotTest --no-daemon 2>&1; then
        echo "ERROR: Batch $batch_num failed! Check output above."
        echo "Generated images so far are preserved in: $REF_DIR"
        exit 1
    fi

    count=$(find "$REF_DIR" -name "*.png" 2>/dev/null | wc -l)
    echo "Batch $batch_num complete. Total images so far: $count"
    echo ""
done

# Count final results
FINAL_COUNT=$(find "$REF_DIR" -name "*.png" 2>/dev/null | wc -l)
EXPECTED=$(( TOTAL * 8 ))  # 8 composables per locale

echo "=== Generation Complete ==="
echo "Generated: $FINAL_COUNT images (expected: $EXPECTED)"

if (( FINAL_COUNT != EXPECTED )); then
    echo "WARNING: Count mismatch! Some screenshots may be missing."
    echo "Check $REF_DIR for details."
fi

echo ""
echo "Next step: run ./fastlane/copy_screenshots.sh to sort into fastlane directories."
