package eu.darken.cap.main.ui

import android.os.Bundle
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.cap.R
import eu.darken.cap.common.navigation.findNavController
import eu.darken.cap.common.smart.SmartActivity
import eu.darken.cap.databinding.MainActivityBinding

@AndroidEntryPoint
class MainActivity : SmartActivity() {

    private val vm: MainActivityVM by viewModels()
    private lateinit var ui: MainActivityBinding
    private val navController by lazy { supportFragmentManager.findNavController(R.id.nav_host) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ui = MainActivityBinding.inflate(layoutInflater)
        setContentView(ui.root)
    }
}
