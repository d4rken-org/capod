package eu.darken.cap.main.ui

import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.cap.R
import eu.darken.cap.common.BuildConfigWrap
import eu.darken.cap.common.debug.logging.log
import eu.darken.cap.common.lists.differ.update
import eu.darken.cap.common.lists.setupDefaults
import eu.darken.cap.common.smart.Smart2Fragment
import eu.darken.cap.common.viewbinding.viewBinding
import eu.darken.cap.databinding.MainFragmentBinding
import javax.inject.Inject

@AndroidEntryPoint
class MainFragment : Smart2Fragment(R.layout.main_fragment) {

    override val vm: MainFragmentVM by viewModels()
    override val ui: MainFragmentBinding by viewBinding()

    @Inject
    lateinit var adapter: MainAdapter

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
            list.setupDefaults(adapter)
        }
        ui.toolbar.apply {
            subtitle = BuildConfigWrap.VERSION_DESCRIPTION
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_item_debuglog -> {
                        vm.toggleDebugLog()
                        true
                    }
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
