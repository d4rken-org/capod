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

    fun sponsor() {
        savedStateHandle[KEY_SPONSOR_OPENED_AT] = SystemClock.elapsedRealtime()
        webpageTool.open("https://github.com/sponsors/d4rken")
    }

    fun onResume() {
        val openedAt = savedStateHandle.get<Long>(KEY_SPONSOR_OPENED_AT) ?: return
        savedStateHandle.remove<Long>(KEY_SPONSOR_OPENED_AT)

        if (SystemClock.elapsedRealtime() - openedAt >= 10_000L) {
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
