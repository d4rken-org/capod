package eu.darken.capod.pods.core.apple.ble.history

import eu.darken.capod.common.fromHex
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.devices.BaseBlePodsTest
import eu.darken.capod.pods.core.apple.ble.devices.airpods.AirPodsPro2Usbc
import eu.darken.capod.profiles.core.AppleDeviceProfile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Reproduces Symptom B (the dashboard-card glitch): a FOREIGN same-model AirPods, whose BLE address
 * does not resolve against our IRK, gets recovered as OUR device's stable identity purely via the
 * fuzzy/case-ignored history fallback ([PodHistoryRepo.baseSearch]). The foreign snapshot carries
 * `meta.profile == null` (IRK failed) but adopts our identifier, so it overwrites our BLE cache slot
 * keyed by that identifier — and the merged device flaps to `ble = null` (cached-only) until our next
 * real frame re-binds.
 *
 * The two frames use the SAME advertisement payload (so the low-entropy fuzzy markers — model bits,
 * pod/case battery nibbles, colour — are identical) but DIFFERENT BLE addresses, mimicking another
 * person's same-model AirPods at a similar battery level in a crowded environment.
 */
class PodHistoryFuzzyCollisionTest : BaseBlePodsTest() {

    // IRK + a resolvable RPA pair lifted from RPACheckerTest.
    private val irkHex = "79-04-65-1E-E2-CC-D9-26-F2-6E-20-EE-3E-CC-DE-79"
    private val myRpa = "5A:16:2B:91:D1:CD"        // resolves against irkHex
    private val foreignAddress = "77:49:4C:D8:25:0C" // does NOT resolve against irkHex

    // A valid AirPods Pro 2 (USB-C) proximity advertisement (model 0x2420), reused for both frames.
    private val payload = "07 19 01 24 20 0B 99 8F 11 00 04 BD A7 3B FF 2D 8A 3C AF 9B 1A 7C 74 B7 A9 D1 C3"

    @Test
    fun `foreign same-model frame must not adopt a keyed device's identity`() = runTest {
        profileList.add(
            AppleDeviceProfile(
                label = "Mine",
                model = PodModel.AIRPODS_PRO2_USBC,
                identityKey = irkHex.fromHex(),
                address = "AA:BB:CC:DD:EE:FF",
            )
        )

        // 1) Our own frame: address resolves against our IRK -> IRK-bound, registers history.
        lateinit var mine: AirPodsPro2Usbc
        create<AirPodsPro2Usbc>(payload, address = myRpa) { mine = this }
        mine.meta.isIRKMatch shouldBe true
        mine.meta.profile shouldNotBe null

        // 2) A foreign device's frame: same payload (identical fuzzy markers), different address.
        lateinit var foreign: AirPodsPro2Usbc
        create<AirPodsPro2Usbc>(payload, address = foreignAddress) { foreign = this }

        // The foreign frame is correctly NOT IRK-bound...
        foreign.meta.isIRKMatch shouldBe false
        foreign.meta.profile shouldBe null

        // ...and therefore must NOT inherit our stable identity. Before the gate this FAILED: the
        // case-ignored/fuzzy fallback recovered our KnownDevice for the foreign frame, so it adopted
        // our identifier and poisoned our cache slot -> the merged device glitched to ble=null.
        foreign.identifier shouldNotBe mine.identifier
    }

    @Test
    fun `our own keyed device keeps its identity across an RPA rotation`() = runTest {
        // Guard: narrowing the weak paths must not fragment our own identity — across an RPA
        // rotation our device is still recovered via the strong IRK path.
        profileList.add(
            AppleDeviceProfile(
                label = "Mine",
                model = PodModel.AIRPODS_PRO2_USBC,
                identityKey = irkHex.fromHex(),
                address = "AA:BB:CC:DD:EE:FF",
            )
        )
        val myRotatedRpa = "45:23:51:E3:40:6E" // also resolves against irkHex (computed)

        lateinit var first: AirPodsPro2Usbc
        create<AirPodsPro2Usbc>(payload, address = myRpa) { first = this }
        first.meta.isIRKMatch shouldBe true

        lateinit var second: AirPodsPro2Usbc
        create<AirPodsPro2Usbc>(payload, address = myRotatedRpa) { second = this }
        second.meta.isIRKMatch shouldBe true
        // Same physical device across rotation -> same stable identity (recovered via IRK).
        second.identifier shouldBe first.identifier
    }
}
