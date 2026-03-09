package eu.darken.capod.common.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object MapIntByteArrayBase64Serializer : KSerializer<Map<Int, ByteArray>> {

    private val delegate = MapSerializer(Int.serializer(), ByteArrayBase64Serializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<Int, ByteArray>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): Map<Int, ByteArray> {
        return delegate.deserialize(decoder)
    }
}
