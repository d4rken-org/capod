package eu.darken.capod.pods.core.apple.ble.protocol

data class ProximityPayload(
    val public: Public,
    val private: Private?,
) {

    data class Public(
        val data: UByteArray,
    )

    data class Private(
        val data: UByteArray,
    )
}
