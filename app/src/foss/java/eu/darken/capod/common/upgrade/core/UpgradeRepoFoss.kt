package eu.darken.capod.common.upgrade.core

import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.upgrade.UpgradeRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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

    // Written only from the sharing coroutine (single collector) — no synchronization needed.
    private var lastKnownInfo: Info? = null

    override val upgradeInfo: Flow<UpgradeRepo.Info> = refreshTrigger
        .flatMapLatest {
            fossCache.upgrade.flow
                .map { data ->
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
                .catch { e ->
                    // A SharedFlow cannot fail: without this, a thrown cache read dies inside
                    // shareIn's sharing coroutine and every collector hangs forever (VM state stuck
                    // on Loading, checkSponsorReturn suspended mid-unlock). The catch sits INSIDE
                    // flatMapLatest so the error completes only this inner subscription — refresh()
                    // resubscribes the cache and recovery stays possible. Last-known preservation:
                    // a late read failure must not revoke an entitlement we already saw; the error
                    // rides on the previous Info instead. Contrast: gplay keeps a retryWhen loop
                    // because billing re-settles in-place — the FOSS read is a local one-shot, and
                    // refresh-driven resubscription IS the retry.
                    if (e is CancellationException) throw e
                    log(TAG, WARN) { "upgradeInfo read failed: ${e.asLog()}" }
                    emit((lastKnownInfo ?: Info()).copy(error = e))
                }
        }
        .onEach { if (it.error == null) lastKnownInfo = it }
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
     * Decode failures therefore fall back to absent by design (the flag), so the error path around
     * [upgradeInfo] covers IO/corruption throws, not schema mismatches.
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
        // A returned transaction proves the store is readable again: revive a possibly error-stuck
        // inner flow so the record propagates to collectors still holding the error replay.
        refresh()
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