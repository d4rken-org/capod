package eu.darken.capod.common.upgrade

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface UpgradeRepo {
    val upgradeInfo: Flow<Info>

    fun getSponsorUrl(): String? = null

    interface Info {
        val type: Type

        val isPro: Boolean

        val upgradedAt: Instant?

        val error: Throwable?
    }

    enum class Type {
        GPLAY,
        FOSS
    }
}