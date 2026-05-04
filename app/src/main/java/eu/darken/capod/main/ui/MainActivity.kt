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
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.navigation.LocalNavigationController
import eu.darken.capod.common.navigation.Nav
import eu.darken.capod.common.navigation.NavigationController
import eu.darken.capod.common.navigation.NavigationEntry
import eu.darken.capod.common.theming.CapodTheme
import eu.darken.capod.common.uix.Activity2
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.currentThemeState
import eu.darken.capod.main.core.themeState
import eu.darken.capod.reaction.ui.popup.PopUpWindow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject
import eu.darken.capod.common.datastore.valueBlocking

@AndroidEntryPoint
class MainActivity : Activity2() {

    @Inject lateinit var navCtrl: NavigationController
    @Inject lateinit var navigationEntries: Set<@JvmSuppressWildcards NavigationEntry>
    @Inject lateinit var generalSettings: GeneralSettings
    @Inject lateinit var popUpWindow: PopUpWindow
    @Inject lateinit var upgradeRepo: UpgradeRepo

    // Buffers warm-start intents (onNewIntent) so they are consumed from inside the Compose tree,
    // after navCtrl.setup(backStack) has run. Calling navCtrl directly from onNewIntent races with
    // the asynchronous Compose lambda and crashes if the back stack is not yet registered.
    private val warmIntents = SingleEventFlow<Intent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        if (intent?.getBooleanExtra(EXTRA_UPGRADE_FOR_RESULT, false) == true) {
            lifecycleScope.launch {
                upgradeRepo.upgradeInfo
                    .filter { it.isPro }
                    .take(1)
                    .collect {
                        log(TAG) { "Upgrade completed, finishing with RESULT_OK" }
                        setResult(RESULT_OK)
                        finish()
                    }
            }
        }

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
                // Cold-start intent (the activity's launching intent).
                consumeUpgradeExtra(intent)
                // Warm-start intents delivered via onNewIntent. Consuming them here (instead of
                // directly from onNewIntent) guarantees navCtrl.setup() has run.
                warmIntents.collect { newIntent -> consumeUpgradeExtra(newIntent) }
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
        setIntent(intent)
        // Defer to the Compose-side collector — navCtrl may not yet be set up here.
        warmIntents.tryEmit(intent)
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
        const val EXTRA_UPGRADE_FOR_RESULT = "upgrade_for_result"
        private val TAG = logTag("MainActivity")
    }
}
