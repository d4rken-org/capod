package eu.darken.capod.main.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.navigation.LocalNavigationController
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.navigation.NavigationController
import eu.darken.capod.common.navigation.NavigationEntry
import eu.darken.capod.common.theming.CapodTheme
import eu.darken.capod.common.uix.Activity2
import eu.darken.capod.main.core.GeneralSettings
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : Activity2() {

    @Inject lateinit var navCtrl: NavigationController
    @Inject lateinit var navigationEntries: Set<@JvmSuppressWildcards NavigationEntry>
    @Inject lateinit var generalSettings: GeneralSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        val startDestination: NavKey = if (generalSettings.isOnboardingDone.value) {
            Nav.Main.Overview
        } else {
            Nav.Main.Onboarding
        }

        setContent {
            val themeState by generalSettings.themeState.collectAsState(initial = generalSettings.currentThemeState)
            val backStack = rememberNavBackStack(startDestination)
            navCtrl.setup(backStack)

            CapodTheme(state = themeState) {
                val backgroundColor = MaterialTheme.colorScheme.background
                val useDarkIcons = backgroundColor.luminance() > 0.5f
                SideEffect {
                    window.decorView.setBackgroundColor(backgroundColor.toArgb())
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = useDarkIcons
                    insetsController.isAppearanceLightNavigationBars = useDarkIcons
                }

                CompositionLocalProvider(LocalNavigationController provides navCtrl) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = {
                            if (!navCtrl.up()) {
                                finish()
                            }
                        },
                        entryProvider = entryProvider {
                            navigationEntries.forEach { entry ->
                                entry.apply {
                                    log(TAG) { "Set up navigation entry: $this" }
                                    setup()
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    companion object {
        private val TAG = logTag("MainActivity")
    }
}
