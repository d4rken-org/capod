package eu.darken.capod.pods.core.apple.aap.protocol

import eu.darken.capod.pods.core.apple.PodModel
import testhelpers.BaseTest

/**
 * Shared base for AAP protocol tests. Provides hex parsing, message builders,
 * and type-safe setting extraction — mirrors [eu.darken.capod.pods.core.apple.ble.devices.BaseBlePodsTest]
 * for the BLE path.
 *
 * Subclasses set [podModel] to get a correctly-configured [profile].
 */
abstract class BaseAapSessionTest : BaseTest() {

    abstract val podModel: PodModel

    protected val profile: DefaultAapDeviceProfile by lazy { DefaultAapDeviceProfile(podModel) }

    // ── Hex parsing ──────────────────────────────────────────

    /** Parse hex string(s) into an [AapMessage]. Multiple args are concatenated. */
    protected fun aapMessage(vararg hexParts: String): AapMessage {
        val bytes = hexParts.joinToString(" ")
            .split(" ")
            .filter { it.isNotBlank() }
            .map { it.toInt(16).toByte() }
            .toByteArray()
        return AapMessage.parse(bytes) ?: error("Failed to parse AapMessage from: ${hexParts.joinToString(" ")}")
    }

    // ── Message builders (hide protocol header bytes) ────────

    /**
     * Build a settings message: header + cmd 0x09 + settingId + values + padding.
     * The `04 00 04 00` header and `09 00` command type are added automatically.
     */
    protected fun settingsMessage(settingId: Int, vararg values: Int): AapMessage {
        val bytes = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x09, 0x00,
            settingId.toByte(),
            *values.map { it.toByte() }.toByteArray(),
            0x00, 0x00, 0x00,
        )
        return AapMessage.parse(bytes)!!
    }

    /**
     * Build a battery message: header + cmd 0x04 + entryBytes.
     * [entryBytes] should start with the count byte followed by 5-byte entries.
     */
    protected fun batteryMessage(vararg entryBytes: Int): AapMessage {
        val payload = entryBytes.map { it.toByte() }.toByteArray()
        val header = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x04, 0x00)
        return AapMessage.parse(header + payload)!!
    }

    // ── Type-safe setting decode ─────────────────────────────

    /** Decode a setting from an [AapMessage], asserting the result is non-null and of type [T]. */
    protected inline fun <reified T : AapSetting> decodeSetting(msg: AapMessage): T {
        val (_, setting) = profile.decodeSetting(msg)
            ?: error("decodeSetting returned null for cmd=0x${"%04X".format(msg.commandType)}")
        return setting as? T
            ?: error("Expected ${T::class.simpleName} but got ${setting::class.simpleName}")
    }

    /** Decode a setting from a hex string, asserting the result is non-null and of type [T]. */
    protected inline fun <reified T : AapSetting> decodeSetting(hex: String): T =
        decodeSetting(aapMessage(hex))
}
