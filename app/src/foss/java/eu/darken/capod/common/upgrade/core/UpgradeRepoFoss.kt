package eu.darken.capod.common.upgrade.core

import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.upgrade.UpgradeRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.shareIn
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeRepoFoss @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val fossCache: FossCache,
    private val webpageTool: WebpageTool,
) : UpgradeRepo {

    override val storeSite: String = STORE_SITE
    override val upgradeSite: String = UPGRADE_SITE
    override val betaSite: String = BETA_SITE

    private val refreshTrigger = MutableStateFlow(UUID.randomUUID())

    override val upgradeInfo: Flow<UpgradeRepo.Info> = combine(
        fossCache.upgrade.flow,
        refreshTrigger
    ) { data, _ ->
        if (data == null) {
            Info()
        } else {
            Info(
                isPro = true,
                upgradedAt = data.upgradedAt,
                upgradeReason = data.reason,
            )
        }
    }
        .setupCommonEventHandlers(TAG) { "upgradeInfo" }
        .shareIn(appScope, SharingStarted.WhileSubscribed(3000L, 0L), replay = 1)

    // Synchronous so the caller learns whether the page actually opened: the FOSS unlock heuristic
    // only arms on a successful launch, and a fire-and-forget coroutine can't report that back.
    fun openGithubSponsorsPage(): Boolean {
        log(TAG) { "openGithubSponsorsPage()" }
        return webpageTool.open(upgradeSite)
    }

    // Writes capod's RETAINED persistence schema: existing supporter records are serialized with
    // `reason` (foss.upgrade.reason.*). Adopting canonical's `upgradeType` schema would decode
    // every stored record as null and strip those supporters' entitlement.
    /**
     * Create-only-if-absent inside the store transaction: an existing record (and its upgradedAt —
     * the user-visible "supporter since" date) is never replaced. The VM-level isPro guard alone is
     * not race-free: it reads a shareIn replay that can be stale. Note the kept record is still
     * re-encoded through the current schema — decoded fields are preserved exactly.
     *
     * Caveat from [FossCache]'s `onErrorFallbackToDefault = true`: a stored record that fails to
     * decode reads as null and therefore counts as ABSENT to this transaction, i.e. it gets
     * replaced. That matches the pre-existing read behaviour — such a record already presents the
     * user as free — and re-creating it on the next successful sponsor visit is the recovery path.
     *
     * @return true if a new record was created, false if an existing record was kept.
     */
    internal suspend fun persistUpgrade(): Boolean {
        log(TAG) { "persistUpgrade()" }
        val updated = fossCache.upgrade.update { existing ->
            existing ?: FossUpgrade(
                upgradedAt = Instant.now(),
                reason = FossUpgrade.Reason.DONATED,
            )
        }
        return if (updated.old == null) {
            true
        } else {
            log(TAG, WARN) {
                "persistUpgrade(): Record already exists (upgradedAt=${updated.old.upgradedAt}), keeping it"
            }
            false
        }
    }

    override suspend fun refresh() {
        log(TAG) { "refresh()" }
        refreshTrigger.value = UUID.randomUUID()
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
        private const val BETA_SITE = "https://github.com/d4rken-org/capod/releases"
        private val TAG = logTag("Upgrade", "Foss", "Repo")
    }
}