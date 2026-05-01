package eu.darken.capod.monitor.core

import eu.darken.capod.pods.core.apple.ble.BlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.HasCase
import eu.darken.capod.pods.core.apple.ble.devices.HasChargeDetectionDual
import eu.darken.capod.pods.core.apple.ble.DualBlePodSnapshot
import eu.darken.capod.pods.core.apple.ble.devices.HasEarDetection
import eu.darken.capod.pods.core.apple.ble.devices.HasEarDetectionDual
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.DualApplePods

import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.ble.devices.SingleApplePods
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityPayload
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class PodDeviceTest : BaseTest() {

    private fun mockDualPod(
        model: PodModel = PodModel.AIRPODS_PRO3,
        leftBattery: Float? = 0.8f,
        rightBattery: Float? = 0.9f,
        caseBattery: Float? = 0.5f,
    ): BlePodSnapshot {
        // DualApplePods implements DualBlePodSnapshot + HasCase + other interfaces
        return mockk<DualApplePods>(relaxed = true) {
            every { this@mockk.model } returns model
            every { batteryLeftPodPercent } returns leftBattery
            every { batteryRightPodPercent } returns rightBattery
            every { batteryCasePercent } returns caseBattery
        }
    }

    @Test
    fun `BLE-only device exposes battery from BLE`() {
        val device = PodDevice(profileId = null, ble = mockDualPod(leftBattery = 0.8f), aap = null)
        device.batteryLeft shouldBe 0.8f
        device.isAapConnected shouldBe false
    }

    @Test
    fun `capabilities come from model features`() {
        val device = PodDevice(
            profileId = null, ble = mockDualPod(model = PodModel.AIRPODS_PRO3),
            aap = null,
        )
        device.hasDualPods shouldBe true
        device.hasCase shouldBe true
        device.hasEarDetection shouldBe true
        device.hasAncControl shouldBe true
    }

    @Test
    fun `Beats Solo 3 has no dual pods or case`() {
        val device = PodDevice(
            profileId = null, ble = mockk(relaxed = true) { every { model } returns PodModel.BEATS_SOLO_3 },
            aap = null,
        )
        device.hasDualPods shouldBe false
        device.hasCase shouldBe false
    }

    @Test
    fun `AAP settings are available when connected`() {
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.AncMode::class to AapSetting.AncMode(
                    AapSetting.AncMode.Value.TRANSPARENCY,
                    listOf(AapSetting.AncMode.Value.ON, AapSetting.AncMode.Value.TRANSPARENCY, AapSetting.AncMode.Value.ADAPTIVE),
                ),
            ),
        )
        val device = PodDevice(profileId = null, ble = mockDualPod(), aap = aap)
        device.isAapConnected shouldBe true
        device.ancMode.shouldNotBeNull()
        device.ancMode!!.current shouldBe AapSetting.AncMode.Value.TRANSPARENCY
    }

    @Test
    fun `ANC mode is null when not AAP connected`() {
        val device = PodDevice(profileId = null, ble = mockDualPod(), aap = null)
        device.ancMode.shouldBeNull()
    }

    @Test
    fun `null BLE gives UNKNOWN model`() {
        val device = PodDevice(profileId = null, ble = null, aap = null)
        device.model shouldBe PodModel.UNKNOWN
    }

    @Test
    fun `identifier delegates to BLE`() {
        val id = BlePodSnapshot.Id()
        val device = PodDevice(
            profileId = null, ble = mockk(relaxed = true) {
                every { identifier } returns id
            },
            aap = null,
        )
        device.identifier shouldBe id
    }

    @Test
    fun `identifier null when BLE null`() {
        val device = PodDevice(profileId = null, ble = null, aap = null)
        device.identifier.shouldBeNull()
    }

    @Test
    fun `signal timing properties delegate to BLE`() {
        val now = Instant.now()
        val earlier = now.minusSeconds(60)
        val device = PodDevice(
            profileId = null, ble = mockk(relaxed = true) {
                every { seenLastAt } returns now
                every { seenFirstAt } returns earlier
                every { signalQuality } returns 0.75f
                every { rssi } returns -50
            },
            aap = null,
        )
        device.seenLastAt shouldBe now
        device.seenFirstAt shouldBe earlier
        device.signalQuality shouldBe 0.75f
        device.rssi shouldBe -50
    }

    @Test
    fun `signal timing defaults when BLE null`() {
        val device = PodDevice(profileId = null, ble = null, aap = null)
        device.seenLastAt.shouldBeNull()
        device.seenFirstAt.shouldBeNull()
        device.signalQuality shouldBe 0f
        device.rssi shouldBe 0
    }

    @Test
    fun `seenLastAt uses AAP lastMessageAt when BLE is null`() {
        val aapMessageAt = Instant.parse("2026-04-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = aapMessageAt,
        )
        val device = PodDevice(profileId = null, ble = null, aap = aap)
        device.seenLastAt shouldBe aapMessageAt
    }

    @Test
    fun `seenLastAt picks max of BLE and AAP timestamps`() {
        val bleSeenAt = Instant.parse("2026-04-01T12:00:00Z")
        val aapMessageAt = bleSeenAt.plusSeconds(25) // AAP is more recent
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { seenLastAt } returns bleSeenAt
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = aapMessageAt,
        )
        val device = PodDevice(profileId = null, ble = ble, aap = aap)
        device.seenLastAt shouldBe aapMessageAt
    }

    @Test
    fun `charging properties delegate to BLE interfaces`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { (this@mockk as HasChargeDetectionDual).isLeftPodCharging } returns true
            every { (this@mockk as HasChargeDetectionDual).isRightPodCharging } returns false
            every { (this@mockk as HasCase).isCaseCharging } returns true
        }
        val device = PodDevice(profileId = null, ble = mock, aap = null)
        device.isLeftPodCharging shouldBe true
        device.isRightPodCharging shouldBe false
        device.isCaseCharging shouldBe true
    }

    @Test
    fun `ear detection properties delegate to BLE interfaces`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { (this@mockk as HasEarDetectionDual).isLeftPodInEar } returns true
            every { (this@mockk as HasEarDetectionDual).isRightPodInEar } returns false
            every { (this@mockk as HasEarDetection).isBeingWorn } returns false
            every { (this@mockk as HasEarDetectionDual).isEitherPodInEar } returns true
        }
        val device = PodDevice(profileId = null, ble = mock, aap = null)
        device.isLeftInEar shouldBe true
        device.isRightInEar shouldBe false
        device.isBeingWorn shouldBe false
        device.isEitherPodInEar shouldBe true
    }

    @Test
    fun `BLE isBeingWorn delegates to HasEarDetection on single-pod devices`() {
        val mock = mockk<SingleApplePods>(relaxed = true, moreInterfaces = arrayOf(HasEarDetection::class)) {
            every { model } returns PodModel.AIRPODS_MAX2
            every { (this@mockk as HasEarDetection).isBeingWorn } returns true
        }
        val device = PodDevice(profileId = null, ble = mock, aap = null)
        device.isBeingWorn shouldBe true

        val mockNotWorn = mockk<SingleApplePods>(relaxed = true, moreInterfaces = arrayOf(HasEarDetection::class)) {
            every { model } returns PodModel.AIRPODS_MAX2
            every { (this@mockk as HasEarDetection).isBeingWorn } returns false
        }
        val deviceNotWorn = PodDevice(profileId = null, ble = mockNotWorn, aap = null)
        deviceNotWorn.isBeingWorn shouldBe false
    }

    @Test
    fun `AAP ear detection preferred over BLE`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { (this@mockk as HasEarDetectionDual).isEitherPodInEar } returns false
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    AapSetting.EarDetection.PodPlacement.IN_EAR,
                    AapSetting.EarDetection.PodPlacement.IN_CASE,
                ),
            ),
        )
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isEitherPodInEar shouldBe true
    }

    @Test
    fun `ear detection falls back to BLE when no AAP ear data`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { (this@mockk as HasEarDetectionDual).isEitherPodInEar } returns true
        }
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isEitherPodInEar shouldBe true
    }

    @Test
    fun `AAP left in ear maps via BLE primaryPod LEFT`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { primaryPod } returns DualBlePodSnapshot.Pod.LEFT
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                ),
            ),
        )
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isLeftInEar shouldBe true
        device.isRightInEar shouldBe false
    }

    @Test
    fun `AAP left in ear maps via BLE primaryPod RIGHT`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { primaryPod } returns DualBlePodSnapshot.Pod.RIGHT
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                ),
            ),
        )
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isLeftInEar shouldBe false
        device.isRightInEar shouldBe true
    }

    @Test
    fun `AAP isBeingWorn true when both pods in ear`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                ),
            ),
        )
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isBeingWorn shouldBe true
    }

    @Test
    fun `AAP isBeingWorn false when one pod not in ear`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
                ),
            ),
        )
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isBeingWorn shouldBe false
    }

    // --- AAP Primary Pod ear detection + microphone tests ---

    @Test
    fun `AAP ear detection maps via AAP primaryPod - no BLE primaryPod needed`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            // BLE primaryPod not set (relaxed mock returns default)
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                ),
                AapSetting.PrimaryPod::class to AapSetting.PrimaryPod(AapSetting.PrimaryPod.Pod.LEFT),
            ),
        )
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isLeftInEar shouldBe true
        device.isRightInEar shouldBe false
    }

    @Test
    fun `AAP primaryPod preferred over BLE primaryPod for ear mapping`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { primaryPod } returns DualBlePodSnapshot.Pod.RIGHT // BLE says RIGHT
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                ),
                AapSetting.PrimaryPod::class to AapSetting.PrimaryPod(AapSetting.PrimaryPod.Pod.LEFT), // AAP says LEFT
            ),
        )
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isLeftInEar shouldBe true  // AAP wins
        device.isRightInEar shouldBe false
    }

    @Test
    fun `ear detection falls back to BLE when AAP primaryPod missing`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { primaryPod } returns DualBlePodSnapshot.Pod.RIGHT
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                ),
                // No PrimaryPod setting — falls back to BLE
            ),
        )
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isLeftInEar shouldBe false
        device.isRightInEar shouldBe true  // BLE says RIGHT is primary
    }

    @Test
    fun `AAP microphone from primaryPod preferred over BLE`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { isLeftPodMicrophone } returns false
            every { isRightPodMicrophone } returns true
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.PrimaryPod::class to AapSetting.PrimaryPod(AapSetting.PrimaryPod.Pod.LEFT),
            ),
        )
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isLeftPodMicrophone shouldBe true   // AAP says LEFT
        device.isRightPodMicrophone shouldBe false
    }

    @Test
    fun `microphone falls back to BLE when no AAP primaryPod`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { isLeftPodMicrophone } returns false
            every { isRightPodMicrophone } returns true
        }
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isLeftPodMicrophone shouldBe false
        device.isRightPodMicrophone shouldBe true  // BLE fallback
    }

    @Test
    fun `both pods out - microphone still shows last primary`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                ),
                AapSetting.PrimaryPod::class to AapSetting.PrimaryPod(AapSetting.PrimaryPod.Pod.RIGHT),
            ),
        )
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isLeftPodMicrophone shouldBe false
        device.isRightPodMicrophone shouldBe true
    }

    @Test
    fun `both pods in case - microphone still shows last primary`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.IN_CASE,
                ),
                AapSetting.PrimaryPod::class to AapSetting.PrimaryPod(AapSetting.PrimaryPod.Pod.LEFT),
            ),
        )
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isLeftPodMicrophone shouldBe true
        device.isRightPodMicrophone shouldBe false
    }

    @Test
    fun `pendingAncMode exposed from AAP state`() {
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            pendingAncMode = AapSetting.AncMode.Value.ADAPTIVE,
        )
        val device = PodDevice(profileId = null, ble = mockDualPod(), aap = aap)
        device.pendingAncMode shouldBe AapSetting.AncMode.Value.ADAPTIVE
    }

    @Test
    fun `pendingAncMode null when no AAP`() {
        val device = PodDevice(profileId = null, ble = mockDualPod(), aap = null)
        device.pendingAncMode.shouldBeNull()
    }

    @Test
    fun `pendingAncMode null when not set`() {
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        val device = PodDevice(profileId = null, ble = mockDualPod(), aap = aap)
        device.pendingAncMode.shouldBeNull()
    }

    // ── Pending Settings ────────────────────────────────────

    @Test
    fun `hasPendingSettings exposed from AAP state`() {
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            pendingSettingsCount = 2,
        )
        val device = PodDevice(profileId = null, ble = mockDualPod(), aap = aap)
        device.hasPendingSettings shouldBe true
    }

    @Test
    fun `hasPendingSettings null when no AAP`() {
        val device = PodDevice(profileId = null, ble = mockDualPod(), aap = null)
        device.hasPendingSettings.shouldBeNull()
    }

    @Test
    fun `hasPendingSettings false when no pending`() {
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        val device = PodDevice(profileId = null, ble = mockDualPod(), aap = aap)
        device.hasPendingSettings shouldBe false
    }

    @Test
    fun `icon and label properties delegate to BLE`() {
        val device = PodDevice(
            profileId = null, ble = mockk(relaxed = true) {
                every { model } returns PodModel.AIRPODS_PRO3
                every { iconRes } returns 42
            },
            aap = null,
        )
        device.iconRes shouldBe 42
    }

    @Test
    fun `rawDataHex empty when BLE null`() {
        val device = PodDevice(profileId = null, ble = null, aap = null)
        device.rawDataHex shouldBe emptyList()
    }

    @Test
    fun `battery falls back to BLE when AAP battery is null`() {
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        val device = PodDevice(profileId = null, ble = mockDualPod(leftBattery = 0.8f), aap = aap)
        device.batteryLeft shouldBe 0.8f
        device.isAapConnected shouldBe true
    }

    @Test
    fun `AAP battery preferred over BLE battery`() {
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            batteries = mapOf(
                AapPodState.BatteryType.LEFT to AapPodState.Battery(AapPodState.BatteryType.LEFT, 0.79f, AapPodState.ChargingState.NOT_CHARGING),
            ),
        )
        val device = PodDevice(profileId = null, ble = mockDualPod(leftBattery = 0.8f), aap = aap)
        device.batteryLeft shouldBe 0.79f  // AAP 1% granularity wins over BLE 10%
    }

    @Test
    fun `AAP charging preferred over BLE charging`() {
        val mock = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { (this@mockk as HasChargeDetectionDual).isLeftPodCharging } returns false
        }
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            batteries = mapOf(
                AapPodState.BatteryType.LEFT to AapPodState.Battery(AapPodState.BatteryType.LEFT, 0.8f, AapPodState.ChargingState.CHARGING_OPTIMIZED),
            ),
        )
        val device = PodDevice(profileId = null, ble = mock, aap = aap)
        device.isLeftPodCharging shouldBe true  // AAP CHARGING_OPTIMIZED counts as charging
    }

    @Test
    fun `address returns bonded address from profile, not BLE RPA`() {
        val bondedAddress = "CC:22:FE:25:69:63"
        val bleRpa = "5A:3B:1C:2D:4E:6F"
        val profile = mockk<eu.darken.capod.profiles.core.AppleDeviceProfile>(relaxed = true) {
            every { address } returns bondedAddress
        }
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { this@mockk.address } returns bleRpa
            every { meta } returns ApplePods.AppleMeta(profile = profile)
        }
        val device = PodDevice(profileId = null, ble = ble, aap = null)
        device.address shouldBe bondedAddress
        device.bleAddress shouldBe bleRpa
    }

    // --- AAP signal quality boost tests ---

    private fun deviceWithBleQuality(bleQuality: Float, aap: AapPodState? = null): PodDevice {
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { signalQuality } returns bleQuality
        }
        return PodDevice(profileId = null, ble = ble, aap = aap)
    }

    @Test
    fun `AAP boost - READY with fresh message applies 0_15`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(5),
        )
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.15f
    }

    @Test
    fun `AAP boost - READY with warm message applies 0_10`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(20),
        )
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.10f
    }

    @Test
    fun `AAP boost - READY with stale message applies 0_05`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(60),
        )
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.05f
    }

    @Test
    fun `AAP boost - boundary at exactly 10s is warm tier`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(10),
        )
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.10f
    }

    @Test
    fun `AAP boost - boundary at exactly 30s is stale tier`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(30),
        )
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.05f
    }

    @Test
    fun `AAP boost - signalQuality clamps to 1_0`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(5),
        )
        val device = deviceWithBleQuality(0.95f, aap)
        device.signalQuality shouldBe 1.0f
    }

    @Test
    fun `AAP boost - CONNECTING state applies 0_05`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.CONNECTING)
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.05f
    }

    @Test
    fun `AAP boost - future timestamp treated as stale`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.plusSeconds(60),
        )
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.05f
    }

    @Test
    fun `AAP boost - READY with null lastMessageAt returns 0_05`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        val device = deviceWithBleQuality(0.50f, aap)
        device.computeAapBoost(now) shouldBe 0.05f
    }

    @Test
    fun `AAP boost - no AAP returns zero`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val device = deviceWithBleQuality(0.50f, aap = null)
        device.computeAapBoost(now) shouldBe 0f
    }

    // --- bleKeyState tests ---

    @Test
    fun `bleKeyState - null BLE returns NONE`() {
        val device = PodDevice(profileId = null, ble = null, aap = null)
        device.bleKeyState shouldBe BleKeyState.NONE
    }

    @Test
    fun `bleKeyState - non-Apple BLE returns NONE`() {
        val device = PodDevice(profileId = null, ble = mockk(relaxed = true) { every { model } returns PodModel.UNKNOWN }, aap = null)
        device.bleKeyState shouldBe BleKeyState.NONE
    }

    @Test
    fun `bleKeyState - Apple BLE without IRK match returns NONE`() {
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { meta } returns ApplePods.AppleMeta(isIRKMatch = false)
            every { payload } returns ProximityPayload(public = ProximityPayload.Public(UByteArray(9)), private = null)
        }
        val device = PodDevice(profileId = null, ble = ble, aap = null)
        device.bleKeyState shouldBe BleKeyState.NONE
    }

    @Test
    fun `bleKeyState - IRK match without private payload returns IRK_ONLY`() {
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { meta } returns ApplePods.AppleMeta(isIRKMatch = true)
            every { payload } returns ProximityPayload(public = ProximityPayload.Public(UByteArray(9)), private = null)
        }
        val device = PodDevice(profileId = null, ble = ble, aap = null)
        device.bleKeyState shouldBe BleKeyState.IRK_ONLY
    }

    @Test
    fun `bleKeyState - IRK match with private payload returns IRK_AND_ENCRYPTED`() {
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { meta } returns ApplePods.AppleMeta(isIRKMatch = true)
            every { payload } returns ProximityPayload(
                public = ProximityPayload.Public(UByteArray(9)),
                private = ProximityPayload.Private(UByteArray(8)),
            )
        }
        val device = PodDevice(profileId = null, ble = ble, aap = null)
        device.bleKeyState shouldBe BleKeyState.IRK_AND_ENCRYPTED
    }

    @Test
    fun `bleKeyState - profile IRK_AND_ENCRYPTED wins while AAP is live with no BLE`() {
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        val device = PodDevice(
            profileId = "p1",
            ble = null,
            aap = aap,
            profileKeyState = BleKeyState.IRK_AND_ENCRYPTED,
        )
        device.bleKeyState shouldBe BleKeyState.IRK_AND_ENCRYPTED
    }

    @Test
    fun `bleKeyState - profile IRK_ONLY shown while AAP is live with no BLE`() {
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        val device = PodDevice(
            profileId = "p1",
            ble = null,
            aap = aap,
            profileKeyState = BleKeyState.IRK_ONLY,
        )
        device.bleKeyState shouldBe BleKeyState.IRK_ONLY
    }

    @Test
    fun `bleKeyState - profile keys ignored when device is not live`() {
        val device = PodDevice(
            profileId = "p1",
            ble = null,
            aap = null,
            profileKeyState = BleKeyState.IRK_AND_ENCRYPTED,
        )
        device.bleKeyState shouldBe BleKeyState.NONE
    }

    @Test
    fun `bleKeyState - profile keys override unmatched live BLE`() {
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { meta } returns ApplePods.AppleMeta(isIRKMatch = false)
            every { payload } returns ProximityPayload(public = ProximityPayload.Public(UByteArray(9)), private = null)
        }
        val device = PodDevice(
            profileId = "p1",
            ble = ble,
            aap = null,
            profileKeyState = BleKeyState.IRK_AND_ENCRYPTED,
        )
        device.bleKeyState shouldBe BleKeyState.IRK_AND_ENCRYPTED
    }

    // --- rssiQuality tests ---

    @Test
    fun `rssiQuality - uses BLE value when BLE present`() {
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { rssiQuality } returns 0.42f
        }
        val device = PodDevice(profileId = null, ble = ble, aap = null)
        device.rssiQuality shouldBe 0.42f
    }

    @Test
    fun `rssiQuality - zero when no BLE and no AAP`() {
        val device = PodDevice(profileId = null, ble = null, aap = null)
        device.rssiQuality shouldBe 0f
    }

    @Test
    fun `rssiQuality - zero when AAP CONNECTING`() {
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.CONNECTING)
        val device = PodDevice(profileId = "p1", ble = null, aap = aap)
        device.rssiQuality shouldBe 0f
    }

    @Test
    fun `rssiQuality - zero when AAP HANDSHAKING`() {
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.HANDSHAKING)
        val device = PodDevice(profileId = "p1", ble = null, aap = aap)
        device.rssiQuality shouldBe 0f
    }

    @Test
    fun `rssiQuality - full when AAP READY regardless of lastMessageAt null`() {
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.READY, lastMessageAt = null)
        val device = PodDevice(profileId = "p1", ble = null, aap = aap)
        device.rssiQuality shouldBe 1.0f
    }

    @Test
    fun `rssiQuality - full when AAP READY with fresh message`() {
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(5),
        )
        val device = PodDevice(profileId = "p1", ble = null, aap = aap)
        device.rssiQuality shouldBe 1.0f
    }

    @Test
    fun `rssiQuality - full when AAP READY with very old message (quiet channel)`() {
        // AirPods don't send periodic AAP messages — a user listening to music with no UI
        // interaction for 10 minutes is still a healthy connection, not a degraded one.
        val now = Instant.parse("2026-01-01T12:00:00Z")
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            lastMessageAt = now.minusSeconds(600),
        )
        val device = PodDevice(profileId = "p1", ble = null, aap = aap)
        device.rssiQuality shouldBe 1.0f
    }

    // --- AAP aggregate ear detection tests ---

    @Test
    fun `per-side in-ear null when AAP ear detection is mixed but no primaryPod and no BLE`() {
        val aap = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                ),
            ),
        )
        val device = PodDevice(profileId = "p1", ble = null, aap = aap, profileModel = PodModel.AIRPODS_PRO3)
        // Per-side is null because resolvedPrimaryPod is null (no AAP PrimaryPod, no BLE)
        device.isLeftInEar.shouldBeNull()
        device.isRightInEar.shouldBeNull()
    }

    @Test
    fun `per-side in-ear resolves when AAP placements match but no primaryPod and no BLE`() {
        val aapBothIn = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                ),
            ),
        )
        val deviceBothIn = PodDevice(profileId = "p1", ble = null, aap = aapBothIn, profileModel = PodModel.AIRPODS_PRO3)
        deviceBothIn.isLeftInEar shouldBe true
        deviceBothIn.isRightInEar shouldBe true

        val aapBothOut = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                ),
            ),
        )
        val deviceBothOut = PodDevice(profileId = "p1", ble = null, aap = aapBothOut, profileModel = PodModel.AIRPODS_PRO3)
        deviceBothOut.isLeftInEar shouldBe false
        deviceBothOut.isRightInEar shouldBe false
    }

    @Test
    fun `isBeingWorn works without resolvedPrimaryPod`() {
        val aapBothIn = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                ),
            ),
        )
        val deviceBothIn = PodDevice(profileId = "p1", ble = null, aap = aapBothIn, profileModel = PodModel.AIRPODS_PRO3)
        deviceBothIn.isBeingWorn shouldBe true

        val aapOneOut = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                ),
            ),
        )
        val deviceOneOut = PodDevice(profileId = "p1", ble = null, aap = aapOneOut, profileModel = PodModel.AIRPODS_PRO3)
        deviceOneOut.isBeingWorn shouldBe false
    }

    @Test
    fun `isEitherPodInEar works without resolvedPrimaryPod`() {
        val aapOneIn = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.IN_EAR,
                ),
            ),
        )
        val device = PodDevice(profileId = "p1", ble = null, aap = aapOneIn, profileModel = PodModel.AIRPODS_PRO3)
        device.isEitherPodInEar shouldBe true

        val aapNoneIn = AapPodState(
            connectionState = AapPodState.ConnectionState.READY,
            settings = mapOf(
                AapSetting.EarDetection::class to AapSetting.EarDetection(
                    primaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                    secondaryPod = AapSetting.EarDetection.PodPlacement.NOT_IN_EAR,
                ),
            ),
        )
        val deviceNone = PodDevice(profileId = "p1", ble = null, aap = aapNoneIn, profileModel = PodModel.AIRPODS_PRO3)
        deviceNone.isEitherPodInEar shouldBe false
    }

    @Test
    fun `rssiQuality - BLE value wins over AAP READY`() {
        val ble = mockk<DualApplePods>(relaxed = true) {
            every { model } returns PodModel.AIRPODS_PRO3
            every { rssiQuality } returns 0.3f
        }
        val aap = AapPodState(connectionState = AapPodState.ConnectionState.READY)
        val device = PodDevice(profileId = "p1", ble = ble, aap = aap)
        device.rssiQuality shouldBe 0.3f
    }
}
