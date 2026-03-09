package eu.darken.capod.profiles.core

import eu.darken.capod.common.serialization.ByteArrayBase64Serializer
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.IdentityResolvingKey
import eu.darken.capod.pods.core.apple.protocol.ProximityEncryptionKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Parcelize
@Serializable
@SerialName("apple")
data class AppleDeviceProfile(
    @SerialName("id") override val id: ProfileId = UUID.randomUUID().toString(),
    @SerialName("label") override val label: String,
    @SerialName("priority") override val priority: Int = 0,
    @SerialName("model") override val model: PodDevice.Model = PodDevice.Model.UNKNOWN,
    @SerialName("minimumSignalQuality") override val minimumSignalQuality: Float = DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY,
    @SerialName("identityKey") @Serializable(with = ByteArrayBase64Serializer::class) val identityKey: IdentityResolvingKey? = null,
    @SerialName("encryptionKey") @Serializable(with = ByteArrayBase64Serializer::class) val encryptionKey: ProximityEncryptionKey? = null,
    @SerialName("address") override val address: String? = null,
) : DeviceProfile