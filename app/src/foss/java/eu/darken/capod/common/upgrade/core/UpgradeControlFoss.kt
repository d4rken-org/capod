package eu.darken.capod.common.upgrade.core

import eu.darken.capod.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeControlFoss @Inject constructor(
    private val fossCache: FossCache,
) : UpgradeRepo {

    override val upgradeInfo: Flow<UpgradeRepo.Info> = fossCache.upgrade.flow.map { data ->
        if (data == null) {
            Info()
        } else {
            Info(
                isPro = true,
                upgradedAt = data.upgradedAt,
                upgradeReason = data.reason
            )
        }
    }

    fun upgrade(reason: FossUpgrade.Reason) {
        fossCache.upgrade.value = FossUpgrade(
            upgradedAt = Instant.now(),
            reason = reason
        )
    }

    data class Info(
        override val isPro: Boolean = false,
        override val upgradedAt: Instant? = null,
        val upgradeReason: FossUpgrade.Reason? = null,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS
    }

    override fun getSponsorUrl(): String = "https://github.com/sponsors/d4rken"

}