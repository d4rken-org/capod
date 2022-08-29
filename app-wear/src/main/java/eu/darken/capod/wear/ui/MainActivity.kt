package eu.darken.capod.wear.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.widget.SwipeDismissFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.navigation.findNavController
import eu.darken.capod.common.uix.Activity2
import eu.darken.capod.databinding.MainActivityBinding

@AndroidEntryPoint
class MainActivity : Activity2() {

    private val vm: MainActivityVM by viewModels()
    private lateinit var ui: MainActivityBinding
    private val navController by lazy { supportFragmentManager.findNavController(R.id.nav_host) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        ui = MainActivityBinding.inflate(layoutInflater)
        setContentView(ui.root)

        ui.swipeWrapper.addCallback(object : SwipeDismissFrameLayout.Callback() {
            override fun onDismissed(layout: SwipeDismissFrameLayout) {
                if (!navController.popBackStack()) finish()
                super.onDismissed(layout)
            }
        })
    }
}
