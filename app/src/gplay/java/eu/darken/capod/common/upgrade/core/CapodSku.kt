package eu.darken.capod.common.upgrade.core

import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.upgrade.core.data.AvailableSku
import eu.darken.capod.common.upgrade.core.data.Sku

enum class CapodSku constructor(override val sku: Sku) : AvailableSku {
    PRO_UPGRADE(Sku("${BuildConfigWrap.APPLICATION_ID}.iap.upgrade.pro"))
}