package eu.darken.capod.devices.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.EdgeToEdgeHelper
import eu.darken.capod.common.lists.differ.update
import eu.darken.capod.common.lists.setupDefaults
import eu.darken.capod.common.uix.Fragment3
import eu.darken.capod.common.viewbinding.viewBinding
import eu.darken.capod.databinding.DeviceManagerFragmentBinding
import javax.inject.Inject

@AndroidEntryPoint
class DeviceManagerFragment : Fragment3(R.layout.device_manager_fragment) {

    override val vm: DeviceManagerFragmentVM by viewModels()
    override val ui: DeviceManagerFragmentBinding by viewBinding()

    @Inject
    lateinit var adapter: DeviceManagerAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.root, left = true, right = true)
            insetsPadding(ui.toolbar, top = true)
        }

        // Handle FAB margins for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(ui.fab) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val baseMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16f,
                resources.displayMetrics
            ).toInt()
            view.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                bottomMargin = baseMargin + systemBars.bottom
                marginEnd = baseMargin + systemBars.right
            }
            insets
        }

        ui.apply {
            list.setupDefaults(adapter, dividers = false)
            
            toolbar.setNavigationOnClickListener {
                vm.onBackPressed()
            }

            fab.setOnClickListener {
                vm.onAddDevice()
            }
        }

        vm.listItems.observe2(ui) { items ->
            adapter.update(items)
        }

        super.onViewCreated(view, savedInstanceState)
    }
}