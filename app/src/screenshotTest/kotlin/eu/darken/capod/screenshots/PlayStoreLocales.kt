package eu.darken.capod.screenshots

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Multi-preview annotation generating one preview per Play Store-supported locale (light mode).
 * Each [name] is the fastlane metadata directory name for direct use in the copy script.
 */
@Preview(locale = "en", name = "en-US", device = DS)
@Preview(locale = "af", name = "af", device = DS)
@Preview(locale = "am", name = "am", device = DS)
@Preview(locale = "ar", name = "ar", device = DS)
annotation class PlayStoreLocales

/**
 * Same locales but with night mode enabled for dark theme screenshots.
 */
@Preview(locale = "en", name = "en-US", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(locale = "af", name = "af", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(locale = "am", name = "am", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(locale = "ar", name = "ar", device = DS, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class PlayStoreLocalesDark

/**
 * Smoke test subset for fast iteration (6 locales covering LTR, RTL, CJK).
 */
@Preview(locale = "en", name = "en-US", device = DS)
annotation class PlayStoreLocalesSmoke
