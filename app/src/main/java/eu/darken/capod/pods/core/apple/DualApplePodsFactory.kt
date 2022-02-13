package eu.darken.capod.pods.core.apple

import eu.darken.capod.common.debug.logging.Logging.Priority.DEBUG
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.pods.core.PodDevice

abstract class DualApplePodsFactory(private val tag: String) : ApplePodsFactory<DualAirPods>(tag) {

    fun DualAirPods.getCaseMatchMarkings() = SplitPodsMarkings(
        leftPodBattery = batteryLeftPodPercent,
        rightPodBattery = batteryRightPodPercent,
        microPhoneLeft = isLeftPodMicrophone,
        microPhoneRight = isRightPodMicrophone,
        chargingLeft = isLeftPodCharging,
        chargingRight = isRightPodCharging,
        color = rawDeviceColor,
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

    private fun Collection<KnownDevice>.findSplitPodsMatch(device: DualAirPods): Collection<KnownDevice> {
        val target = device.getCaseMatchMarkings()

        return filter { known ->
            known.history
                .filterIsInstance<DualAirPods>()
                .any { it.getCaseMatchMarkings() == target }
        }
    }

    override fun searchHistory(current: DualAirPods): KnownDevice? {
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