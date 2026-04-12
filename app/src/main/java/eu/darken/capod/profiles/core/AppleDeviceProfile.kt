package eu.darken.capod.profiles.core

import eu.darken.capod.common.serialization.ByteArrayBase64Serializer
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.protocol.IdentityResolvingKey
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityEncryptionKey
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
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
    @SerialName("model") override val model: PodModel = PodModel.UNKNOWN,
    @SerialName("minimumSignalQuality") override val minimumSignalQuality: Float = DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY,
    @SerialName("identityKey") @Serializable(with = ByteArrayBase64Serializer::class) val identityKey: IdentityResolvingKey? = null,
    @SerialName("encryptionKey") @Serializable(with = ByteArrayBase64Serializer::class) val encryptionKey: ProximityEncryptionKey? = null,
    @SerialName("address") override val address: String? = null,
    @SerialName("reactionAutoPause") val autoPause: Boolean = false,
    @SerialName("reactionAutoPlay") val autoPlay: Boolean = false,
    @SerialName("reactionOnePodMode") val onePodMode: Boolean = false,
    @SerialName("reactionAutoConnect") val autoConnect: Boolean = false,
    @SerialName("reactionAutoConnectCondition") val autoConnectCondition: AutoConnectCondition = AutoConnectCondition.WHEN_SEEN,
    @SerialName("reactionShowPopUpOnCaseOpen") val showPopUpOnCaseOpen: Boolean = false,
    @SerialName("reactionShowPopUpOnConnection") val showPopUpOnConnection: Boolean = false,
) : DeviceProfile, HasReactionConfig {

    override val reactionConfig: ReactionConfig
        get() = ReactionConfig(
            autoPause = autoPause,
            autoPlay = autoPlay,
            onePodMode = onePodMode,
            autoConnect = autoConnect,
            autoConnectCondition = autoConnectCondition,
            showPopUpOnCaseOpen = showPopUpOnCaseOpen,
            showPopUpOnConnection = showPopUpOnConnection,
        )
}