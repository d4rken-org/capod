package eu.darken.capod.upgrade.ui

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.uix.ViewModel4
import eu.darken.capod.common.upgrade.core.FossUpgrade
import eu.darken.capod.common.upgrade.core.UpgradeControlFoss
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    dispatcherProvider: DispatcherProvider,
    private val upgradeControlFoss: UpgradeControlFoss,
    private val webpageTool: WebpageTool,
) : ViewModel4(dispatcherProvider) {

    sealed interface SponsorEvent {
        data object ReturnedTooEarly : SponsorEvent
    }

    val sponsorEvents = SingleEventFlow<SponsorEvent>()

    data class State(
        val isPro: Boolean = false,
        val upgradedAt: Instant? = null,
    )

    // Null until DataStore answered: a defaulted isPro=false would flash the sales route (and its
    // armable unlock heuristic) at an existing supporter opening their status.
    val state: StateFlow<State?> = upgradeControlFoss.upgradeInfo
        .map { info -> State(isPro = info.isPro, upgradedAt = info.upgradedAt) }
        .stateIn(vmScope, SharingStarted.WhileSubscribed(5_000), null)

    fun sponsor() {
        savedStateHandle[KEY_SPONSOR_OPENED_AT] = SystemClock.elapsedRealtime()
        webpageTool.open("https://github.com/sponsors/d4rken")
    }

    // Plain sponsor link for existing supporters: must NOT arm the unlock heuristic — re-running
    // it would rewrite the "supporter since" date and navigate away from the status view.
    fun openSponsorPage() {
        webpageTool.open("https://github.com/sponsors/d4rken")
    }

    fun onResume() {
        val openedAt = savedStateHandle.get<Long>(KEY_SPONSOR_OPENED_AT) ?: return
        savedStateHandle.remove<Long>(KEY_SPONSOR_OPENED_AT)

        if (SystemClock.elapsedRealtime() - openedAt >= 5_000L) {
            upgradeControlFoss.upgrade(FossUpgrade.Reason.DONATED)
            navUp()
        } else {
            sponsorEvents.tryEmit(SponsorEvent.ReturnedTooEarly)
        }
    }

    companion object {
        private const val KEY_SPONSOR_OPENED_AT = "sponsor_opened_at"
    }
}
