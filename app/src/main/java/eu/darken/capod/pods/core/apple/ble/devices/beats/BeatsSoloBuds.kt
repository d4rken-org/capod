package eu.darken.capod.pods.core.apple.ble.devices.beats

import androidx.annotation.DrawableRes
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.ble.devices.ApplePodsFactory
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.pods.core.apple.ble.history.PodHistoryRepo
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityMessage
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPairing
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPayload
import java.time.Instant
import javax.inject.Inject

data class BeatsSoloBuds(
    override val identifier: BlePodSnapshot.Id = BlePodSnapshot.Id(),
    override val seenLastAt: Instant = Instant.now(),
    override val seenFirstAt: Instant = Instant.now(),
    override val seenCounter: Int = 1,
    override val scanResult: BleScanResult,
    override val payload: ProximityPayload,
    override val meta: ApplePods.AppleMeta,
    override val reliability: Float = BlePodSnapshot.BASE_CONFIDENCE,
    private val rssiAverage: Int? = null,
    private val cachedBatteryPercentage: Float? = null,
    private val cachedCaseState: DualApplePods.LidState? = null
) : DualApplePods {

    override val model: PodModel = PodModel.BEATS_SOLO_BUDS

    @get:DrawableRes
    override val leftPodIcon: Int
        get() = R.drawable.device_beats_fitpro_left

    @get:DrawableRes
    override val rightPodIcon: Int
        get() = R.drawable.device_beats_fitpro_right

    @get:DrawableRes
    override val caseIcon: Int
        get() = R.drawable.device_beats_fitpro_case

    override val batteryCasePercent: Float?
        get() = super.batteryCasePercent ?: cachedBatteryPercentage

    override val caseLidState: DualApplePods.LidState
        get() = cachedCaseState ?: super.caseLidState

    override val rssi: Int
        get() = rssiAverage ?: super.rssi

    class Factory @Inject constructor(
        private val repo: PodHistoryRepo,
    ) : ApplePodsFactory {
        override fun isResponsible(message: ProximityMessage): Boolean = message.run {
            getModelInfo().full == DEVICE_CODE && length == ProximityPairing.PAIRING_MESSAGE_LENGTH
        }

        override suspend fun create(
            scanResult: BleScanResult,
            payload: ProximityPayload,
            meta: ApplePods.AppleMeta
        ): ApplePods {
            var basic = BeatsSoloBuds(scanResult = scanResult, payload = payload, meta = meta)
            val result = repo.search(basic)

            if (result != null) basic = basic.copy(identifier = result.id)
            repo.updateHistory(basic)

            if (result == null) return basic

            return basic.copy(
                identifier = result.id,
                seenFirstAt = result.seenFirstAt,
                seenLastAt = scanResult.receivedAt,
                seenCounter = result.seenCounter,
                reliability = result.reliability,
                cachedBatteryPercentage = result.getLatestCaseBattery(),
                rssiAverage = result.rssiSmoothed(basic.rssi),
                cachedCaseState = result.getLatestCaseLidState(basic)
            )
        }
    }

    companion object {
        private val DEVICE_CODE = 0x2620.toUShort()
        private val TAG = logTag("PodDevice", "Beats", "Solo", "Buds")
    }
}
