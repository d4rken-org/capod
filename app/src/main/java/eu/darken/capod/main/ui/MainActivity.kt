package eu.darken.capod.main.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : Activity2() {

    @Inject lateinit var navCtrl: NavigationController
    @Inject lateinit var navigationEntries: Set<@JvmSuppressWildcards NavigationEntry>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        setContent {
            val backStack = rememberNavBackStack(Nav.Main.Overview)
            navCtrl.setup(backStack)

            CapodTheme {
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
