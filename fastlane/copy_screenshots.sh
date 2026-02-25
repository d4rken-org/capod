#!/usr/bin/env bash
set -euo pipefail

# Copies generated screenshot PNGs from the Compose Preview reference directory
# into fastlane metadata directories for Play Store upload.
#
# Usage:
#   ./fastlane/copy_screenshots.sh           # Copy all screenshots
#   ./fastlane/copy_screenshots.sh --clean   # Clean target dirs before copying

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REF_DIR="$PROJECT_DIR/app/src/screenshotTestGplayDebug/reference/eu/darken/capod/screenshots/PlayStoreScreenshotsKt"
FASTLANE_DIR="$PROJECT_DIR/fastlane/metadata/android"

CLEAN=false
if [[ "${1:-}" == "--clean" ]]; then
    CLEAN=true
fi

# Map composable function names to screenshot filenames.
# Format: number_label — number controls Play Store ordering, label aids humans.
declare -A SCREEN_MAP=(
    [DashboardLight]="1_dashboard_light"
    [DashboardDark]="2_dashboard_dark"
    [DeviceProfiles]="3_device_profiles"
    [AddProfile]="4_add_profile"
    [SettingsIndex]="5_settings"
    [ReactionSettings]="6_reaction_settings"
    [WidgetConfiguration]="7_widget_configuration"
)

if [[ ! -d "$REF_DIR" ]]; then
    echo "ERROR: Reference directory not found: $REF_DIR"
    echo "Run ./fastlane/generate_screenshots.sh first."
    exit 1
fi

# Count available images
AVAILABLE=$(find "$REF_DIR" -name "*.png" | wc -l)
if (( AVAILABLE == 0 )); then
    echo "ERROR: No PNG files found in $REF_DIR"
    exit 1
fi
echo "Found $AVAILABLE screenshot images"

if $CLEAN; then
    echo "Cleaning existing phoneScreenshots directories..."
    find "$FASTLANE_DIR" -path "*/images/phoneScreenshots" -type d -exec rm -rf {} + 2>/dev/null || true
fi

COPIED=0
ERRORS=0

for png in "$REF_DIR"/*.png; do
    filename=$(basename "$png")

    # Filename format: FunctionName_previewName_hash_index.png
    # Split from the right: remove _index.png, then _hash
    # FunctionName has no underscores (camelCase), previewName may have hyphens

    # Remove .png extension
    stem="${filename%.png}"

    # Remove trailing _index (digit(s))
    stem="${stem%_[0-9]*}"

    # Remove trailing _hash (hex string)
    stem="${stem%_[a-f0-9]*}"

    # Now stem = FunctionName_previewName
    # Split at first underscore
    func_name="${stem%%_*}"
    locale_name="${stem#*_}"

    if [[ -z "${SCREEN_MAP[$func_name]+x}" ]]; then
        echo "WARNING: Unknown function name '$func_name' in $filename — skipping"
        (( ERRORS++ )) || true
        continue
    fi

    screen_name="${SCREEN_MAP[$func_name]}"
    target_dir="$FASTLANE_DIR/$locale_name/images/phoneScreenshots"

    mkdir -p "$target_dir"
    cp "$png" "$target_dir/${screen_name}.png"
    (( COPIED++ )) || true
done

echo ""
echo "=== Copy Complete ==="
echo "Copied: $COPIED images"
if (( ERRORS > 0 )); then
    echo "Errors: $ERRORS"
fi

# Validate: check each locale has the expected number of screenshots
echo ""
echo "Validation:"
INCOMPLETE=0
for locale_dir in "$FASTLANE_DIR"/*/images/phoneScreenshots; do
    if [[ ! -d "$locale_dir" ]]; then
        continue
    fi
    locale=$(echo "$locale_dir" | sed "s|$FASTLANE_DIR/||" | sed 's|/images/phoneScreenshots||')
    count=$(find "$locale_dir" -name "*.png" | wc -l)
    expected=${#SCREEN_MAP[@]}
    if (( count != expected )); then
        echo "  INCOMPLETE: $locale has $count/${expected} screenshots"
        (( INCOMPLETE++ )) || true
    fi
done

if (( INCOMPLETE == 0 )); then
    echo "  All locales have complete screenshot sets."
else
    echo "  $INCOMPLETE locale(s) have incomplete screenshot sets."
fi
