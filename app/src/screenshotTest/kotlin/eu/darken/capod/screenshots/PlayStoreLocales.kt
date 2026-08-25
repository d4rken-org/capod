package eu.darken.capod.screenshots

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Placeholder locale set. `fastlane/generate_screenshots.sh` rewrites this file per batch and
 * restores it afterwards, so the committed content only decides what a bare
 * `./gradlew updateGplayDebugScreenshotTest` renders.
 *
 * [name] is the fastlane metadata directory the copy script sorts the output into.
 */
@Preview(locale = "en", name = "en-US", device = DS)
annotation class PlayStoreLocales

/**
 * Same locales but with night mode enabled for dark theme screenshots.
 */
@Preview(locale = "en", name = "en-US", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class PlayStoreLocalesDark
