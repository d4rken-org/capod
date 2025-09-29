package eu.darken.capod.profiles.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
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

    private var isDragging = false

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

            toolbar.setNavigationOnClickListener { vm.onBackPressed() }

            fab.setOnClickListener { vm.onAddDevice() }

            val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val fromPosition = viewHolder.adapterPosition
                    val toPosition = target.adapterPosition

                    // Only allow reordering of profile items, not empty state cards or hints
                    if (adapter.data[fromPosition] is DeviceProfileVH.Item &&
                        adapter.data[toPosition] is DeviceProfileVH.Item
                    ) {
                        return adapter.moveItem(fromPosition, toPosition)
                    }
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    // No swipe to dismiss
                }

                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    when (actionState) {
                        ItemTouchHelper.ACTION_STATE_DRAG -> {
                            isDragging = true
                        }

                        ItemTouchHelper.ACTION_STATE_IDLE -> {
                            if (isDragging) vm.onProfilesReordered(adapter.getItems())
                        }
                    }
                }

                override fun isLongPressDragEnabled(): Boolean = true
                override fun isItemViewSwipeEnabled(): Boolean = false
            })
            itemTouchHelper.attachToRecyclerView(list)
        }

        vm.listItems.observe2(ui) { items ->
            adapter.update(items)
            isDragging = false
        }

        super.onViewCreated(view, savedInstanceState)
    }
}