package eu.darken.capod.common.compose.preview

import android.content.Context
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.common.upgrade.UpgradeRepo
import eu.darken.capod.pods.core.DualPodDevice
import eu.darken.capod.pods.core.HasCase
import eu.darken.capod.pods.core.HasChargeDetection
import eu.darken.capod.pods.core.HasChargeDetectionDual
import eu.darken.capod.pods.core.HasDualMicrophone
import eu.darken.capod.pods.core.HasEarDetection
import eu.darken.capod.pods.core.HasEarDetectionDual
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.SinglePodDevice
import eu.darken.capod.pods.core.unknown.UnknownDevice
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import java.time.Instant

/** Fixed timestamp for deterministic preview/screenshot rendering. */
val MOCK_NOW: Instant = Instant.parse("2025-06-15T10:30:00Z")

object MockPodDataProvider {

    fun dummyScanResult(rssi: Int = -50): BleScanResult = BleScanResult(
        receivedAt = MOCK_NOW,
        address = "AA:BB:CC:DD:EE:FF",
        rssi = rssi,
        generatedAtNanos = 0L,
        manufacturerSpecificData = mapOf(
            76 to byteArrayOf(0x07, 0x19, 0x01, 0x02, 0x20, 0x75, 0xAA.toByte(), 0x30, 0x01, 0x00, 0x00, 0x2D)
        ),
    )

    // --- Dual pod scenarios ---

    fun airPodsProFullCharge(): DualPodDevice = MockDualPodDevice(
        _model = PodDevice.Model.AIRPODS_PRO2,
        _label = "Work AirPods",
        batteryLeftPodPercent = 1.0f,
        batteryRightPodPercent = 1.0f,
        _batteryCasePercent = 1.0f,
        leftPodIcon = R.drawable.device_airpods_pro2_left,
        rightPodIcon = R.drawable.device_airpods_pro2_right,
        _caseIcon = R.drawable.device_airpods_pro2_case,
    )

    fun airPodsProMixed(): DualPodDevice = MockDualPodDevice(
        _model = PodDevice.Model.AIRPODS_PRO2,
        _label = "My AirPods Pro",
        batteryLeftPodPercent = 0.80f,
        batteryRightPodPercent = 0.45f,
        _batteryCasePercent = 0.60f,
        _isLeftPodCharging = true,
        leftPodIcon = R.drawable.device_airpods_pro2_left,
        rightPodIcon = R.drawable.device_airpods_pro2_right,
        _caseIcon = R.drawable.device_airpods_pro2_case,
    )

    fun airPodsProLowBattery(): DualPodDevice = MockDualPodDevice(
        _model = PodDevice.Model.AIRPODS_PRO2,
        _label = "My AirPods Pro",
        batteryLeftPodPercent = 0.10f,
        batteryRightPodPercent = 0.05f,
        _batteryCasePercent = null,
        _isLeftPodInEar = true,
        _isRightPodInEar = true,
        leftPodIcon = R.drawable.device_airpods_pro2_left,
        rightPodIcon = R.drawable.device_airpods_pro2_right,
        _caseIcon = R.drawable.device_airpods_pro2_case,
    )

    fun airPodsProInCase(): DualPodDevice = MockDualPodDevice(
        _model = PodDevice.Model.AIRPODS_PRO2,
        _label = "My AirPods Pro",
        batteryLeftPodPercent = null,
        batteryRightPodPercent = null,
        _batteryCasePercent = 0.90f,
        leftPodIcon = R.drawable.device_airpods_pro2_left,
        rightPodIcon = R.drawable.device_airpods_pro2_right,
        _caseIcon = R.drawable.device_airpods_pro2_case,
    )

    fun airPodsGen1Wearing(): DualPodDevice = MockDualPodDevice(
        _model = PodDevice.Model.AIRPODS_GEN1,
        _label = "Old AirPods",
        batteryLeftPodPercent = 0.70f,
        batteryRightPodPercent = 0.65f,
        _batteryCasePercent = 0.50f,
        _isLeftPodInEar = true,
        _isRightPodInEar = true,
        _isLeftPodMicrophone = true,
        leftPodIcon = R.drawable.device_airpods_gen1_left,
        rightPodIcon = R.drawable.device_airpods_gen1_right,
        _caseIcon = R.drawable.device_airpods_gen1_case,
    )

    fun powerBeatsPro(): DualPodDevice = MockDualPodDeviceNoCase(
        _model = PodDevice.Model.POWERBEATS_PRO,
        _label = "PowerBeats Pro",
        batteryLeftPodPercent = 0.55f,
        batteryRightPodPercent = 0.60f,
        leftPodIcon = R.drawable.device_powerbeats_pro_left,
        rightPodIcon = R.drawable.device_powerbeats_pro_right,
    )

    // --- Single pod scenarios ---

    fun airPodsMax(): SinglePodDevice = MockSinglePodDevice(
        _model = PodDevice.Model.AIRPODS_MAX,
        _label = "AirPods Max",
        batteryHeadsetPercent = 0.85f,
        _isBeingWorn = true,
    )

    fun airPodsMaxCharging(): SinglePodDevice = MockSinglePodDevice(
        _model = PodDevice.Model.AIRPODS_MAX,
        _label = "AirPods Max",
        batteryHeadsetPercent = 0.40f,
        _isHeadsetBeingCharged = true,
    )

    fun beatsSolo3(): SinglePodDevice = MockSinglePodDevice(
        _model = PodDevice.Model.BEATS_SOLO_3,
        _label = "Beats Solo 3",
        batteryHeadsetPercent = 0.70f,
    )

    // --- Unknown device ---

    fun unknownDevice(): PodDevice = UnknownDevice(
        scanResult = dummyScanResult(rssi = -70),
    )

    // --- Profile helpers ---

    fun profile(label: String, model: PodDevice.Model): AppleDeviceProfile = AppleDeviceProfile(
        label = label,
        model = model,
    )

    // --- UpgradeInfo ---

    fun fossInfo(isPro: Boolean = false): UpgradeRepo.Info = MockUpgradeInfo(
        type = UpgradeRepo.Type.FOSS,
        isPro = isPro,
    )

    fun gplayInfo(isPro: Boolean = false): UpgradeRepo.Info = MockUpgradeInfo(
        type = UpgradeRepo.Type.GPLAY,
        isPro = isPro,
    )
}

private data class MockUpgradeInfo(
    override val type: UpgradeRepo.Type,
    override val isPro: Boolean,
    override val upgradedAt: Instant? = null,
    override val error: Throwable? = null,
) : UpgradeRepo.Info

private class MockDualPodDevice(
    private val _model: PodDevice.Model,
    private val _label: String,
    override val batteryLeftPodPercent: Float?,
    override val batteryRightPodPercent: Float?,
    private val _batteryCasePercent: Float?,
    private val _isCaseCharging: Boolean = false,
    private val _isLeftPodCharging: Boolean = false,
    private val _isRightPodCharging: Boolean = false,
    private val _isLeftPodInEar: Boolean = false,
    private val _isRightPodInEar: Boolean = false,
    private val _isLeftPodMicrophone: Boolean = false,
    private val _isRightPodMicrophone: Boolean = false,
    override val leftPodIcon: Int = R.drawable.device_airpods_gen1_left,
    override val rightPodIcon: Int = R.drawable.device_airpods_gen1_right,
    private val _caseIcon: Int = R.drawable.device_airpods_gen1_case,
    rssi: Int = -50,
) : DualPodDevice, HasCase, HasChargeDetectionDual, HasEarDetectionDual, HasDualMicrophone {
    override val identifier: PodDevice.Id = PodDevice.Id()
    override val model: PodDevice.Model = _model
    override val seenLastAt: Instant = MOCK_NOW
    override val seenFirstAt: Instant = MOCK_NOW
    override val seenCounter: Int = 5
    override val scanResult: BleScanResult = MockPodDataProvider.dummyScanResult(rssi)
    override val reliability: Float = 1.0f
    override val signalQuality: Float = 0.75f
    override val iconRes: Int = _model.iconRes
    override val meta: PodDevice.Meta = object : PodDevice.Meta {
        override val profile: DeviceProfile = AppleDeviceProfile(label = _label, model = _model)
    }

    override fun getLabel(context: Context): String = _model.label

    // HasCase
    override val batteryCasePercent: Float? = _batteryCasePercent
    override val isCaseCharging: Boolean = _isCaseCharging
    override val caseIcon: Int = _caseIcon

    // HasChargeDetectionDual
    override val isLeftPodCharging: Boolean = _isLeftPodCharging
    override val isRightPodCharging: Boolean = _isRightPodCharging

    // HasEarDetectionDual
    override val isLeftPodInEar: Boolean = _isLeftPodInEar
    override val isRightPodInEar: Boolean = _isRightPodInEar

    // HasDualMicrophone
    override val isLeftPodMicrophone: Boolean = _isLeftPodMicrophone
    override val isRightPodMicrophone: Boolean = _isRightPodMicrophone
}

private class MockDualPodDeviceNoCase(
    private val _model: PodDevice.Model,
    private val _label: String,
    override val batteryLeftPodPercent: Float?,
    override val batteryRightPodPercent: Float?,
    override val leftPodIcon: Int = R.drawable.device_airpods_gen1_left,
    override val rightPodIcon: Int = R.drawable.device_airpods_gen1_right,
    rssi: Int = -50,
) : DualPodDevice, HasChargeDetectionDual, HasEarDetectionDual {
    override val identifier: PodDevice.Id = PodDevice.Id()
    override val model: PodDevice.Model = _model
    override val seenLastAt: Instant = MOCK_NOW
    override val seenFirstAt: Instant = MOCK_NOW
    override val seenCounter: Int = 5
    override val scanResult: BleScanResult = MockPodDataProvider.dummyScanResult(rssi)
    override val reliability: Float = 1.0f
    override val signalQuality: Float = 0.70f
    override val iconRes: Int = _model.iconRes
    override val meta: PodDevice.Meta = object : PodDevice.Meta {
        override val profile: DeviceProfile = AppleDeviceProfile(label = _label, model = _model)
    }

    override fun getLabel(context: Context): String = _model.label

    // HasChargeDetectionDual
    override val isLeftPodCharging: Boolean = false
    override val isRightPodCharging: Boolean = false

    // HasEarDetectionDual
    override val isLeftPodInEar: Boolean = false
    override val isRightPodInEar: Boolean = false
}

private class MockSinglePodDevice(
    private val _model: PodDevice.Model,
    private val _label: String,
    override val batteryHeadsetPercent: Float?,
    private val _isHeadsetBeingCharged: Boolean = false,
    private val _isBeingWorn: Boolean = false,
    rssi: Int = -50,
) : SinglePodDevice, HasChargeDetection, HasEarDetection {
    override val identifier: PodDevice.Id = PodDevice.Id()
    override val model: PodDevice.Model = _model
    override val seenLastAt: Instant = MOCK_NOW
    override val seenFirstAt: Instant = MOCK_NOW
    override val seenCounter: Int = 5
    override val scanResult: BleScanResult = MockPodDataProvider.dummyScanResult(rssi)
    override val reliability: Float = 1.0f
    override val signalQuality: Float = 0.80f
    override val iconRes: Int = _model.iconRes
    override val meta: PodDevice.Meta = object : PodDevice.Meta {
        override val profile: DeviceProfile = AppleDeviceProfile(label = _label, model = _model)
    }

    override fun getLabel(context: Context): String = _model.label

    // HasChargeDetection
    override val isHeadsetBeingCharged: Boolean = _isHeadsetBeingCharged

    // HasEarDetection
    override val isBeingWorn: Boolean = _isBeingWorn
}
