package eu.darken.capod.main.ui.overview.cards

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.databinding.MainPermissionItemBinding
import eu.darken.capod.main.ui.overview.OverviewAdapter

class PermissionCardVH(parent: ViewGroup) :
    OverviewAdapter.BaseVH<PermissionCardVH.Item, MainPermissionItemBinding>(
        R.layout.main_permission_item,
        parent
    ) {

    override val viewBinding = lazy {
        MainPermissionItemBinding.bind(itemView)
    }

    override val onBindData: MainPermissionItemBinding.(
        item: Item,
        payloads: List<Any>
    ) -> Unit = { item, _ ->
        permissionLabel.setText(item.permission.labelRes)
        permissionDescription.setText(item.permission.descriptionRes)
        grantAction.setOnClickListener { item.onRequest(item.permission) }
    }

    data class Item(
        val permission: Permission,
        val onRequest: (Permission) -> Unit
    ) : OverviewAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }
}