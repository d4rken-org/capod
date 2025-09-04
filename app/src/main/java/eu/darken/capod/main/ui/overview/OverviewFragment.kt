package eu.darken.capod.main.ui.overview

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.BuildConfig
import eu.darken.capod.R
import eu.darken.capod.common.EdgeToEdgeHelper
import eu.darken.capod.common.colorString
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.lists.differ.update
import eu.darken.capod.common.lists.setupDefaults
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.uix.Fragment3
import eu.darken.capod.common.upgrade.UpgradeRepo
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
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.toolbar, top = true)
            insetsPadding(ui.list, bottom = false)
        }
        ui.apply {
            list.setupDefaults(adapter, dividers = false)
        }

        ui.toolbar.apply {
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_item_devices -> {
                        vm.goToDeviceManager()
                        true
                    }

                    R.id.menu_item_settings -> {
                        vm.goToSettings()
                        true
                    }

                    R.id.menu_item_donate -> {
                        vm.onUpgrade()
                        true
                    }

                    R.id.menu_item_upgrade -> {
                        vm.onUpgrade()
                        true
                    }

                    else -> false
                }
            }
        }

        vm.listItems.observe2(ui) {
            if (BuildConfig.DEBUG) toolbar.subtitle = "${it.size} items"
            adapter.update(it)
        }

        vm.workerAutolaunch.observe2 {
            // While UI is active, subscribe to the autolaunch routine
        }

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

        vm.upgradeState.observe2(ui) { info ->
            val gplay = toolbar.menu.findItem(R.id.menu_item_upgrade)
            val donate = toolbar.menu.findItem(R.id.menu_item_donate)
            gplay.isVisible = false
            donate.isVisible = false

            val baseTitle = when (info.type) {
                UpgradeRepo.Type.GPLAY -> {
                    if (info.isPro) {
                        getString(eu.darken.capod.common.R.string.app_name_pro)
                    } else {
                        gplay.isVisible = true
                        getString(eu.darken.capod.common.R.string.app_name)
                    }
                }

                UpgradeRepo.Type.FOSS -> {
                    if (info.isPro) {
                        getString(eu.darken.capod.common.R.string.app_name_foss)
                    } else {
                        donate.isVisible = true
                        getString(eu.darken.capod.common.R.string.app_name)
                    }
                }
            }.split(" ".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()

            toolbar.title = if (baseTitle.size == 2) {
                val builder = SpannableStringBuilder(baseTitle[0] + " ")
                val color = when (info.type) {
                    UpgradeRepo.Type.FOSS -> R.color.brand_secondary
                    else -> R.color.brand_tertiary
                }
                builder.append(colorString(requireContext(), color, baseTitle[1]))
            } else {
                getString(eu.darken.capod.common.R.string.app_name)
            }
        }
        vm.launchUpgradeFlow.observe2 {
            it(requireActivity())
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
