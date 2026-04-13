package eu.darken.capod.pods.core.apple.ble.history

import eu.darken.capod.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.lowerNibble
import eu.darken.capod.common.upperNibble
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.logSummary
import eu.darken.capod.pods.core.apple.ble.devices.HasCase
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPayload
import eu.darken.capod.pods.core.apple.ble.protocol.RPAChecker
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.profiles.core.currentProfiles
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodHistoryRepo @Inject constructor(
    private val profilesRepo: DeviceProfilesRepo,
    private val rpaChecker: RPAChecker,
) {

    private val knownDevices = mutableMapOf<BlePodSnapshot.Id, KnownDevice>()

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
        val model: PodModel,
    )

    suspend fun search(current: ApplePods): KnownDevice? {
        val basicResult = baseSearch(current)

        val caseIgnored = if (current is DualApplePods) {
            val target = current.getCaseMatchMarkings()
            knownDevices.values.filter { known ->
                known.history.filterIsInstance<DualApplePods>().any { it.getCaseMatchMarkings() == target }
            }
        } else {
            emptySet()
        }

        if (caseIgnored.isNotEmpty()) {
            log(TAG, VERBOSE) {
                "search2: caseIgnoredMatches=${caseIgnored.joinToString { it.logSummary() }}"
            }
        }

        return when (caseIgnored.size) {
            0 -> basicResult
            1 -> caseIgnored.single()
            else -> {
                log(TAG, WARN) { "search2: More than one result when ignoring case markers." }
                val oldest = caseIgnored.maxByOrNull { it.history.size } ?: return null

                caseIgnored.minus(oldest).forEach {
                    log(TAG, WARN) { "search2: Removing outlier ${it.logSummary()}" }
                    knownDevices.remove(it.id)
                }

                oldest
            }
        }
    }

    private suspend fun baseSearch(current: ApplePods): KnownDevice? {
        val scanResult = current.scanResult
        val payload = current.payload

        // Timeout
        knownDevices.values.toList()
            .filter { it.isOlderThan(Duration.ofSeconds(30)) }
            .forEach { knownDevice ->
                log(TAG, VERBOSE) { "search1: Removing stale ${knownDevice.logSummary()}" }
                knownDevices.remove(knownDevice.id)
            }

        // Trim history
        knownDevices.values
            .filter { it.history.size > KnownDevice.MAX_HISTORY }
            .toList()
            .forEach { knownDevices[it.id] = it.copy(history = it.history.takeLast(KnownDevice.MAX_HISTORY)) }

        var recognizedDevice: KnownDevice? = knownDevices.values
            .firstOrNull { it.lastAddress == scanResult.address }
            ?.also { log(TAG, VERBOSE) { "search1: Recovered via address: ${it.logSummary()}" } }

        if (recognizedDevice == null) {
            val profile = profilesRepo.currentProfiles()
                .filterIsInstance<AppleDeviceProfile>()
                .filter { it.identityKey != null }
                .firstOrNull { rpaChecker.verify(current.address, it.identityKey!!) }

            if (profile != null) {
                recognizedDevice = knownDevices.values
                    .firstOrNull { rpaChecker.verify(it.lastAddress, profile.identityKey!!) }
                    .also { log(TAG, VERBOSE) { "search1: Recovered via IRK: ${it?.logSummary()}" } }
            }
        }

        if (recognizedDevice == null) {
            val currentMarkers = payload.getFuzzyIdentifier()
            recognizedDevice = knownDevices.values
                .firstOrNull { it.lastPayload.getFuzzyIdentifier() == currentMarkers }
                ?.also { log(TAG, DEBUG) { "search1: Similarity match for device=${current.model}" } }
        }

        if (recognizedDevice == null) {
            log(TAG, DEBUG) { "search1: No history match for ${current.logSummary()}" }
        }

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
            log(TAG, DEBUG) { "Creating new history for ${device.logSummary()}" }
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
