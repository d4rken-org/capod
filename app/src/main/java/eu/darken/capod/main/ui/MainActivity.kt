package eu.darken.capod.main.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
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
import eu.darken.capod.main.core.currentThemeState
import eu.darken.capod.main.core.themeState
import eu.darken.capod.reaction.ui.popup.PopUpWindow
import javax.inject.Inject
import eu.darken.capod.common.datastore.valueBlocking

@AndroidEntryPoint
class MainActivity : Activity2() {

    @Inject lateinit var navCtrl: NavigationController
    @Inject lateinit var navigationEntries: Set<@JvmSuppressWildcards NavigationEntry>
    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var popUpWindow: PopUpWindow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        val startDestination: NavKey = if (generalSettings.isOnboardingDone.valueBlocking) {
            Nav.Main.Overview
        } else {
            Nav.Main.Onboarding
        }

        setContent {
            val themeState by generalSettings.themeState.collectAsStateWithLifecycle(initialValue = generalSettings.currentThemeState)
            val backStack = rememberNavBackStack(startDestination)
            navCtrl.setup(backStack)

            LaunchedEffect(Unit) {
                consumeUpgradeExtra(intent)
            }

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
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
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

    override fun onResume() {
        super.onResume()
        popUpWindow.isMainActivityVisible = true
        popUpWindow.close()
    }

    override fun onPause() {
        popUpWindow.isMainActivityVisible = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeUpgradeExtra(intent)
    }

    private fun consumeUpgradeExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_NAVIGATE_TO_UPGRADE, false) == true) {
            intent.removeExtra(EXTRA_NAVIGATE_TO_UPGRADE)
            if (generalSettings.isOnboardingDone.valueBlocking) {
                navCtrl.goTo(Nav.Main.Upgrade)
            }
        }
    }

    companion object {
        const val EXTRA_NAVIGATE_TO_UPGRADE = "navigate_to_upgrade"
        private val TAG = logTag("MainActivity")
    }
}
