package eu.darken.capod.pods.core.apple.aap.protocol

/**
 * Device identity parsed from the AAP handshake response (message type 0x1D).
 */
data class AapDeviceInfo(
    val name: String,
    val modelNumber: String,
    val manufacturer: String,
    val serialNumber: String,
    val firmwareVersion: String,
)
