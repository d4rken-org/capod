package eu.darken.capod.main.ui.overview.cards

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.lists.binding
import eu.darken.capod.common.lists.differ.DifferItem
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.databinding.OverviewPermissionItemBinding
import eu.darken.capod.main.ui.overview.OverviewAdapter

class PermissionCardVH(parent: ViewGroup) :
    OverviewAdapter.BaseVH<PermissionCardVH.Item, OverviewPermissionItemBinding>(
        R.layout.overview_permission_item,
        parent
    ) {

    override val viewBinding = lazy {
        OverviewPermissionItemBinding.bind(itemView)
    }

    override val onBindData: OverviewPermissionItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = binding(payload = true) { item ->
        permissionLabel.setText(item.permission.labelRes)
        permissionDescription.setText(item.permission.descriptionRes)
        grantAction.setOnClickListener { item.onRequest(item.permission) }
        ppAction.setOnClickListener {
            item.onPPAction()
        }
    }

    data class Item(
        val permission: Permission,
        val onRequest: (Permission) -> Unit,
        val onPPAction: () -> Unit
    ) : OverviewAdapter.Item {
        override val stableId: Long = permission.hashCode().toLong()

        override val payloadProvider: ((DifferItem, DifferItem) -> DifferItem?)
            get() = { old, new -> if (new::class.isInstance(old)) new else null }
    }
}