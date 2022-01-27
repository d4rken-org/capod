package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.airpods.AirPodsPro

abstract class DualApplePodsFactory(private val tag: String) : ApplePodsFactory<DualApplePods>(tag) {

    fun DualApplePods.getCaseMatchMarkings() = SplitPodsMarkings(
        leftPodBattery = batteryLeftPodPercent,
        rightPodBattery = batteryRightPodPercent,
        microPhoneLeft = isLeftPodMicrophone,
        microPhoneRight = isRightPodMicrophone,
        chargingLeft = isLeftPodCharging,
        chargingRight = isRightPodCharging,
        color = deviceColor,
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
        val color: DualApplePods.DeviceColor,
        val model: PodDevice.Model,
    )

    private fun Collection<KnownDevice>.findSplitPodsMatch(device: DualApplePods): Collection<KnownDevice> {
        val target = device.getCaseMatchMarkings()

        return filter { known ->
            known.history
                .filterIsInstance<DualApplePods>()
                .any { it.getCaseMatchMarkings() == target }
        }
    }

    fun KnownDevice.getLatestCaseBattery(): Float? = history
        .filterIsInstance<DualApplePods>()
        .mapNotNull { it.batteryCasePercent }
        .lastOrNull()

    fun KnownDevice.getLatestCaseLidState(basic: DualApplePods): DualApplePods.LidState? {
        val definitive = setOf(DualApplePods.LidState.OPEN, DualApplePods.LidState.CLOSED)
        if (definitive.contains(basic.caseLidState)) return null

        return history
            .filterIsInstance<AirPodsPro>()
            .lastOrNull { it.caseLidState != DualApplePods.LidState.NOT_IN_CASE }
            ?.caseLidState
    }

    override fun searchHistory(current: DualApplePods): KnownDevice? {
        val basicResult = super.searchHistory(current)

        val caseIgnored = knownDevices.values.findSplitPodsMatch(current)

        log(tag, DEBUG) { "searchHistory2: Case ignored matches(${caseIgnored.size}): $caseIgnored" }

        return when (caseIgnored.size) {
            0 -> basicResult
            1 -> caseIgnored.single()
            else -> {
                log(tag) { "searchHistory2:  More than one result when ignoring case markers." }
                val oldest = caseIgnored.maxByOrNull { it.history.size } ?: return null

                caseIgnored.minus(oldest).forEach {
                    log(tag) { "searchHistory2: Removing outlier: $it" }
                    knownDevices.remove(it.id)
                }

                oldest
            }
        }
    }

}