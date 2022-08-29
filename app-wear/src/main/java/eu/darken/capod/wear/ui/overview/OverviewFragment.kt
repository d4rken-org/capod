package eu.darken.capod.wear.ui.overview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.lists.differ.update
import eu.darken.capod.common.lists.setupDefaults
import eu.darken.capod.common.permissions.Permission
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
    var awaitingPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        awaitingPermission = savedInstanceState?.getBoolean("awaitingPermission") ?: false

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            log { "Request for $id was granted=$granted" }
            vm.onPermissionResult(granted)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.apply {
            list.setupDefaults(adapter)
        }

        vm.listItems.observe2(ui) { adapter.update(it) }

        vm.requestPermissionEvent.observe2(ui) {
            when (it) {
                Permission.IGNORE_BATTERY_OPTIMIZATION -> {
                    awaitingPermission = true
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${requireContext().packageName}")
                        )
                    )
                }
                Permission.SYSTEM_ALERT_WINDOW -> {
                    awaitingPermission = true
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${requireContext().packageName}")
                        )
                    )
                }
                else -> {
                    permissionLauncher.launch(it.permissionId)
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("awaitingPermission", awaitingPermission)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (awaitingPermission) {
            awaitingPermission = false
            log { "awaitingPermission=true" }
            vm.onPermissionResult(true)
        }
    }
}
