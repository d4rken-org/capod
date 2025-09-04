package eu.darken.capod.pods.core.apple.history

import eu.darken.capod.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.upperNibble
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.ApplePods
import eu.darken.capod.pods.core.apple.DualApplePods
import eu.darken.capod.pods.core.apple.protocol.ProximityPayload
import eu.darken.capod.pods.core.apple.protocol.RPAChecker
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodHistoryRepo @Inject constructor(
    private val generalSettings: GeneralSettings,
    private val rpaChecker: RPAChecker,
) {

    private val knownDevices = mutableMapOf<PodDevice.Id, KnownDevice>()

    data class Identifier(
        val device: UShort,
        val podBatteryData: Set<UShort>,
        val caseBatteryData: UShort,
        val deviceColor: UByte,
    )

    private fun ProximityPayload.getFuzzyIdentifier(): Identifier = Identifier(
        device = (((public.data[1].toInt() and 255) shl 8) or (public.data[2].toInt() and 255)).toUShort(),
        // Make comparison order independent
        podBatteryData = setOf(public.data[4].upperNibble, public.data[4].lowerNibble),
        caseBatteryData = public.data[5].lowerNibble,
        deviceColor = public.data[7]
    )

    fun DualApplePods.getCaseMatchMarkings() = SplitPodsMarkings(
        leftPodBattery = batteryLeftPodPercent,
        rightPodBattery = batteryRightPodPercent,
        microPhoneLeft = isLeftPodMicrophone,
        microPhoneRight = isRightPodMicrophone,
        chargingLeft = isLeftPodCharging,
        chargingRight = isRightPodCharging,
        color = pubDeviceColor,
        model = model
    )

    /**
     * Split pods, one in case, one not.
     */
    data class SplitPodsMarkings(
        val leftPodBattery: Float?,
        val rightPodBattery: Float?,
        val microPhoneLeft: Boolean,
        val microPhoneRight: Boolean,
        val chargingLeft: Boolean,
        val chargingRight: Boolean,
        val color: UByte,
        val model: PodDevice.Model,
    )

    fun search(current: ApplePods): KnownDevice? {
        val basicResult = baseSearch(current)

        val caseIgnored = if (current is DualApplePods) {
            val target = current.getCaseMatchMarkings()
            knownDevices.values.filter { known ->
                known.history.filterIsInstance<DualApplePods>().any { it.getCaseMatchMarkings() == target }
            }
        } else {
            emptySet()
        }

        log(TAG, DEBUG) { "search2: Case ignored matches(${caseIgnored.size}): $caseIgnored" }

        return when (caseIgnored.size) {
            0 -> basicResult
            1 -> caseIgnored.single()
            else -> {
                log(TAG) { "search2:  More than one result when ignoring case markers." }
                val oldest = caseIgnored.maxByOrNull { it.history.size } ?: return null

                caseIgnored.minus(oldest).forEach {
                    log(TAG) { "search2: Removing outlier: $it" }
                    knownDevices.remove(it.id)
                }

                oldest
            }
        }
    }

    private fun baseSearch(current: ApplePods): KnownDevice? {
        val scanResult = current.scanResult
        val payload = current.payload

        // Timeout
        knownDevices.values.toList()
            .filter { it.isOlderThan(Duration.ofSeconds(30)) }
            .forEach { knownDevice ->
                log(TAG, VERBOSE) { "search1: Removing stale known device: $knownDevice" }
                knownDevices.remove(knownDevice.id)
            }

        // Trim history
        knownDevices.values
            .filter { it.history.size > KnownDevice.MAX_HISTORY }
            .toList()
            .forEach { knownDevices[it.id] = it.copy(history = it.history.takeLast(KnownDevice.MAX_HISTORY)) }

        var recognizedDevice: KnownDevice? = knownDevices.values
            .firstOrNull { it.lastAddress == scanResult.address }
            ?.also { log(TAG, VERBOSE) { "search1: Recovered previous ID via address: $it" } }

        if (recognizedDevice == null) {
            generalSettings.mainDeviceIdentityKey.value?.let { key ->
                if (!rpaChecker.verify(current.address, key)) {
                    log(TAG, VERBOSE) { "search1: Current device does not match IRK" }
                    return@let
                }

                recognizedDevice = knownDevices.values
                    .firstOrNull { rpaChecker.verify(it.lastAddress, key) }
                    .also { log(TAG, VERBOSE) { "search1: Recovered previous ID via IRK: $it" } }
            }
        }

        if (recognizedDevice == null) {
            val currentMarkers = payload.getFuzzyIdentifier()
            recognizedDevice = knownDevices.values
                .firstOrNull { it.lastPayload.getFuzzyIdentifier() == currentMarkers }
                ?.also { log(TAG) { "search1: Close match based on similarity: $currentMarkers" } }
        }

        if (recognizedDevice == null) log(TAG, WARN) { "search1: Didn't recognize: $current" }

        return recognizedDevice
    }

    fun updateHistory(device: ApplePods) {
        val existing = knownDevices[device.identifier]

        fun Collection<ApplePods>.determineLatestCaseBattery(): Float? = this
            .filterIsInstance<HasCase>()
            .mapNotNull { it.batteryCasePercent }
            .lastOrNull()

        knownDevices[device.identifier] = if (existing != null) {
            val history = existing.history.plus(device)
            existing.copy(
                seenCounter = existing.seenCounter + 1,
                history = history,
                lastCaseBattery = history.determineLatestCaseBattery() ?: existing.lastCaseBattery
            )
        } else {
            log(TAG) { "Creating new history for $device" }
            val history = listOf(device)
            KnownDevice(
                id = device.identifier,
                seenFirstAt = device.seenFirstAt,
                seenCounter = 1,
                history = history,
                lastCaseBattery = history.determineLatestCaseBattery()
            )
        }
    }

    companion object {
        private val TAG = logTag("Pod", "Apple", "Factory", "History")
    }
}