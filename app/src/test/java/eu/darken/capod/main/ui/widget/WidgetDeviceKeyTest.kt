package eu.darken.capod.main.ui.widget

import eu.darken.capod.common.bluetooth.BleScanResult
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.DualBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.SingleBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.HasCase
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetection
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetectionDual
import eu.darken.capod.pods.core.apple.ble.devices.HasEarDetection
import eu.darken.capod.pods.core.apple.ble.devices.HasEarDetectionDual
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class WidgetDeviceKeyTest : BaseTest() {

    private val dualProfile = AppleDeviceProfile(
        id = "dual-profile",
        label = "Office AirPods",
        model = PodModel.AIRPODS_PRO3,
        address = "AA:BB:CC:DD:EE:FF",
    )

    private val singleProfile = AppleDeviceProfile(
        id = "single-profile",
        label = "Desk AirPods Max",
        model = PodModel.AIRPODS_MAX,
        address = "11:22:33:44:55:66",
    )

    @Test
    fun `scan noise does not change key`() {
        val first = dualDevice(
            ble = FakeDualBlePod(
                profile = dualProfile,
                rssiValue = -59,
                reliability = 0.64f,
                seenCounter = 19,
                seenLastAt = Instant.parse("2026-04-05T18:00:00Z"),
            )
        )
        val second = dualDevice(
            ble = FakeDualBlePod(
                profile = dualProfile,
                rssiValue = -54,
                reliability = 1.0f,
                seenCounter = 31,
                seenLastAt = Instant.parse("2026-04-05T18:00:12Z"),
            )
        )

        first.toWidgetKey() shouldBe second.toWidgetKey()
    }

    @Test
    fun `battery and charging changes alter key`() {
        val base = dualDevice().toWidgetKey()

        dualDevice(ble = FakeDualBlePod(profile = dualProfile, batteryLeftPodPercent = 0.7f))
            .toWidgetKey() shouldNotBe base
        dualDevice(ble = FakeDualBlePod(profile = dualProfile, batteryCasePercent = 0.5f))
            .toWidgetKey() shouldNotBe base
        dualDevice(ble = FakeDualBlePod(profile = dualProfile, isLeftPodCharging = true))
            .toWidgetKey() shouldNotBe base
    }

    @Test
    fun `labels and wear state alter key`() {
        val dualBase = dualDevice().toWidgetKey()

        dualDevice(label = "Renamed AirPods").toWidgetKey() shouldNotBe dualBase
        dualDevice(ble = FakeDualBlePod(profile = dualProfile, isLeftPodInEar = false))
            .toWidgetKey() shouldNotBe dualBase

        val singleBase = singleDevice(ble = FakeSingleBlePod(profile = singleProfile, isBeingWorn = true))
            .toWidgetKey()
        singleDevice(ble = FakeSingleBlePod(profile = singleProfile, isBeingWorn = false))
            .toWidgetKey() shouldNotBe singleBase
    }

    @Test
    fun `aap and anc render state alter key`() {
        val connecting = dualDevice(
            aap = AapPodState(connectionState = AapPodState.ConnectionState.CONNECTING)
        ).toWidgetKey()
        val ready = dualDevice(
            aap = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        ).toWidgetKey()

        ready shouldNotBe connecting

        val ancOn = dualDevice(aap = ancState(current = AapSetting.AncMode.Value.ON)).toWidgetKey()
        val ancTransparency = dualDevice(aap = ancState(current = AapSetting.AncMode.Value.TRANSPARENCY)).toWidgetKey()
        val ancPending = dualDevice(
            aap = ancState(
                current = AapSetting.AncMode.Value.ON,
                pending = AapSetting.AncMode.Value.TRANSPARENCY,
            )
        ).toWidgetKey()
        val ancExpandedModes = dualDevice(
            aap = ancState(
                current = AapSetting.AncMode.Value.ON,
                supported = listOf(
                    AapSetting.AncMode.Value.ON,
                    AapSetting.AncMode.Value.TRANSPARENCY,
                    AapSetting.AncMode.Value.ADAPTIVE,
                ),
            )
        ).toWidgetKey()

        ancTransparency shouldNotBe ancOn
        ancPending shouldNotBe ancOn
        ancExpandedModes shouldNotBe ancOn
    }

    private fun dualDevice(
        label: String? = dualProfile.label,
        ble: BlePodSnapshot? = FakeDualBlePod(profile = dualProfile),
        aap: AapPodState? = null,
    ) = PodDevice(
        profileId = dualProfile.id,
        label = label,
        ble = ble,
        aap = aap,
        profileAddress = dualProfile.address,
        profileModel = dualProfile.model,
    )

    private fun singleDevice(
        label: String? = singleProfile.label,
        ble: BlePodSnapshot? = FakeSingleBlePod(profile = singleProfile),
        aap: AapPodState? = null,
    ) = PodDevice(
        profileId = singleProfile.id,
        label = label,
        ble = ble,
        aap = aap,
        profileAddress = singleProfile.address,
        profileModel = singleProfile.model,
    )

    private fun ancState(
        current: AapSetting.AncMode.Value,
        pending: AapSetting.AncMode.Value? = null,
        supported: List<AapSetting.AncMode.Value> = listOf(
            AapSetting.AncMode.Value.ON,
            AapSetting.AncMode.Value.TRANSPARENCY,
        ),
    ) = AapPodState(
        connectionState = AapPodState.ConnectionState.READY,
        pendingAncMode = pending,
        settings = mapOf(
            AapSetting.AncMode::class to AapSetting.AncMode(current = current, supported = supported),
            AapSetting.AllowOffOption::class to AapSetting.AllowOffOption(enabled = true),
            AapSetting.ListeningModeCycle::class to AapSetting.ListeningModeCycle(modeMask = 0x0F),
        ),
    )

    private data class FakeDualBlePod(
        val profile: DeviceProfile,
        override val batteryLeftPodPercent: Float? = 0.9f,
        override val batteryRightPodPercent: Float? = 1.0f,
        override val batteryCasePercent: Float? = 0.8f,
        override val isLeftPodCharging: Boolean = false,
        override val isRightPodCharging: Boolean = false,
        override val isCaseCharging: Boolean = false,
        override val isLeftPodInEar: Boolean = true,
        override val isRightPodInEar: Boolean = true,
        val rssiValue: Int = -60,
        override val reliability: Float = 0.8f,
        override val seenCounter: Int = 1,
        override val seenLastAt: Instant = Instant.parse("2026-04-05T18:00:00Z"),
        override val seenFirstAt: Instant = Instant.parse("2026-04-05T17:59:00Z"),
    ) : DualBlePodSnapshot, HasChargeDetectionDual, HasEarDetectionDual, HasCase {
        override val identifier: BlePodSnapshot.Id = BlePodSnapshot.Id()
        override val model: PodModel = profile.model
        override val scanResult: BleScanResult = BleScanResult(
            receivedAt = seenLastAt,
            address = profile.address ?: "AA:BB:CC:DD:EE:FF",
            rssi = rssiValue,
            generatedAtNanos = seenCounter.toLong(),
            manufacturerSpecificData = emptyMap(),
        )
        override val meta: BlePodSnapshot.Meta = object : BlePodSnapshot.Meta {
            override val profile: DeviceProfile = this@FakeDualBlePod.profile
        }
    }

    private data class FakeSingleBlePod(
        val profile: DeviceProfile,
        override val batteryHeadsetPercent: Float? = 0.7f,
        override val isHeadsetBeingCharged: Boolean = false,
        override val isBeingWorn: Boolean = true,
        val rssiValue: Int = -60,
        override val reliability: Float = 0.8f,
        override val seenCounter: Int = 1,
        override val seenLastAt: Instant = Instant.parse("2026-04-05T18:00:00Z"),
        override val seenFirstAt: Instant = Instant.parse("2026-04-05T17:59:00Z"),
    ) : SingleBlePodSnapshot, HasChargeDetection, HasEarDetection {
        override val identifier: BlePodSnapshot.Id = BlePodSnapshot.Id()
        override val model: PodModel = profile.model
        override val scanResult: BleScanResult = BleScanResult(
            receivedAt = seenLastAt,
            address = profile.address ?: "11:22:33:44:55:66",
            rssi = rssiValue,
            generatedAtNanos = seenCounter.toLong(),
            manufacturerSpecificData = emptyMap(),
        )
        override val meta: BlePodSnapshot.Meta = object : BlePodSnapshot.Meta {
            override val profile: DeviceProfile = this@FakeSingleBlePod.profile
        }
    }
}
