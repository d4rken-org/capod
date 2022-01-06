package eu.darken.capod.main.ui.overview

import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.lists.differ.update
import eu.darken.capod.common.lists.setupDefaults
import eu.darken.capod.common.uix.Fragment3
import eu.darken.capod.common.viewbinding.viewBinding
import eu.darken.capod.databinding.MainFragmentBinding
import javax.inject.Inject

@AndroidEntryPoint
class OverviewFragment : Fragment3(R.layout.main_fragment) {

    override val vm: OverviewFragmentVM by viewModels()
    override val ui: MainFragmentBinding by viewBinding()

    @Inject
    lateinit var adapter: OverviewAdapter

    lateinit var permissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            log { "Request for $id was granted=$granted" }
            vm.onPermissionResult(granted)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.apply {
            list.setupDefaults(adapter, dividers = false)
        }

        ui.toolbar.apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_item_settings -> {
                        vm.goToSettings()
                        true
                    }
                    else -> false
                }
            }
        }

        vm.listItems.observe2(ui) {
            adapter.update(it)
        }

        vm.requestPermissionevent.observe2(ui) {
            permissionLauncher.launch(it.permissionId)
        }
        super.onViewCreated(view, savedInstanceState)
    }
}
