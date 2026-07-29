package eu.darken.capod.common.upgrade.core

import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.upgrade.UpgradeRepo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import eu.darken.capod.common.datastore.valueBlocking

@Singleton
class UpgradeControlFoss @Inject constructor(
    private val fossCache: FossCache,
) : UpgradeRepo {

    override val storeSite: String = STORE_SITE
    override val upgradeSite: String = UPGRADE_SITE
    override val betaSite: String = BETA_SITE

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
        fossCache.upgrade.valueBlocking = FossUpgrade(
            upgradedAt = Instant.now(),
            reason = reason
        )
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        // The FOSS entitlement is a local cache read that the upgradeInfo flow already observes,
        // there is no remote state to reconcile.
    }

    data class Info(
        override val isPro: Boolean = false,
        override val upgradedAt: Instant? = null,
        val upgradeReason: FossUpgrade.Reason? = null,
        override val error: Throwable? = null,
    ) : UpgradeRepo.Info {
        override val type: UpgradeRepo.Type = UpgradeRepo.Type.FOSS

        // The FOSS entitlement is a local cache read — authoritative from the first emission,
        // there is no billing handshake to wait out.
        override val isSettled: Boolean = true
    }

    companion object {
        private const val STORE_SITE = "https://github.com/d4rken-org/capod/releases"
        private const val UPGRADE_SITE = "https://github.com/sponsors/d4rken"
        private const val BETA_SITE = "https://play.google.com/apps/testing/eu.darken.capod"
        private val TAG = logTag("Upgrade", "Foss", "Control")
    }
}