package eu.darken.capod.main.ui.cards

import android.view.ViewGroup
import eu.darken.capod.R
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.databinding.MainPermissionItemBinding
import eu.darken.capod.main.ui.MainAdapter

class PermissionCardVH(parent: ViewGroup) :
    MainAdapter.BaseVH<PermissionCardVH.Item, MainPermissionItemBinding>(
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
    ) : MainAdapter.Item {
        override val stableId: Long = this.javaClass.hashCode().toLong()
    }
}