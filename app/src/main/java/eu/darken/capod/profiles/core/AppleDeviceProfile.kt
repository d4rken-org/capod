package eu.darken.capod.profiles.core

import eu.darken.capod.common.serialization.ByteArrayBase64Serializer
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting
import eu.darken.capod.pods.core.apple.ble.protocol.IdentityResolvingKey
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityEncryptionKey
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import eu.darken.capod.reaction.core.charged.ChargedSlotScope
import eu.darken.capod.reaction.core.conversation.ConversationAction
import eu.darken.capod.reaction.core.stem.StemActionsConfig
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
    @SerialName("reactionStartMusicOnWear") val startMusicOnWear: Boolean = false,
    @SerialName("reactionOnePodMode") val onePodMode: Boolean = false,
    @SerialName("reactionAutoConnect") val autoConnect: Boolean = true,
    @SerialName("companionAssociationPrompted") val companionAssociationPrompted: Boolean = false,
    @SerialName("experimentalAudioConnectOnAcl") val audioConnectOnAcl: Boolean = false,
    @SerialName("reactionAutoConnectCondition") val autoConnectCondition: AutoConnectCondition = AutoConnectCondition.CASE_OPEN,
    @SerialName("reactionShowPopUpOnCaseOpen") val showPopUpOnCaseOpen: Boolean = false,
    @SerialName("reactionShowPopUpOnConnection") val showPopUpOnConnection: Boolean = false,
    @SerialName("reactionConversationAction") val conversationAction: ConversationAction = ConversationAction.NOTHING,
    @SerialName("reactionConversationVolumeReduction") val conversationVolumeReduction: Int = ReactionConfig.DEFAULT_CONVERSATION_VOLUME_REDUCTION,
    @SerialName("reactionNotifyWhenCharged") val notifyWhenCharged: Boolean = false,
    @SerialName("reactionChargedThreshold") val chargedThreshold: Int = ReactionConfig.DEFAULT_CHARGED_THRESHOLD,
    @SerialName("reactionChargedSlotScope") val chargedSlotScope: ChargedSlotScope = ChargedSlotScope.PODS_AND_CASE,
    @SerialName("reactionNotifyWhenCaseLow") val notifyWhenCaseLow: Boolean = false,
    @SerialName("reactionCaseLowThreshold") val caseLowThreshold: Int = ReactionConfig.DEFAULT_CASE_LOW_THRESHOLD,
    /** Whether the dashboard battery time-remaining estimate is shown for this device. */
    @SerialName("batteryEstimateEnabled") val batteryEstimateEnabled: Boolean = true,
    /**
     * Last-known device-side AllowOffOption (AAP setting 0x34). Persisted so the UI can honor
     * the learned value across sessions — AAP state is dropped on disconnect, but whether OFF
     * mode is allowed on the device is effectively sticky until the owner toggles it.
     */
    @SerialName("learnedAllowOffEnabled") val learnedAllowOffEnabled: Boolean? = null,
    /**
     * Last-known device-side ListeningModeCycle mask (AAP setting 0x1A). The device never
     * echoes this back as a push setting, so without persistence every reconnect resets the
     * UI to the default 0x0E (no OFF bit) even if the real cycle on-device includes OFF.
     */
    @SerialName("learnedListeningModeCycleMask") val lastRequestedListeningModeCycleMask: Int? = null,
    /** Requested preference, applied on ready AAP sessions; the pods do not echo these writes. */
    @SerialName("lastRequestedConnectionPreference") val lastRequestedConnectionPreference: AapSetting.ConnectionPreference.Mode? = null,
    @SerialName("stemActions") val stemActions: StemActionsConfig = StemActionsConfig(),
) : DeviceProfile, HasReactionConfig {

    val autoConnectMode: AapSetting.ConnectionPreference.Mode
        get() = if ((autoConnect || audioConnectOnAcl) && lastRequestedConnectionPreference != AapSetting.ConnectionPreference.Mode.OFF) {
            lastRequestedConnectionPreference ?: AapSetting.ConnectionPreference.Mode.AUTOMATIC
        } else AapSetting.ConnectionPreference.Mode.OFF

    override val reactionConfig: ReactionConfig
        get() = ReactionConfig(
            autoPause = autoPause,
            autoPlay = autoPlay,
            startMusicOnWear = startMusicOnWear,
            onePodMode = onePodMode,
            autoConnect = autoConnectMode != AapSetting.ConnectionPreference.Mode.OFF,
            // Keep old serialized WHEN_SEEN values readable, but use the incoming-link condition.
            autoConnectCondition = if (autoConnectCondition == AutoConnectCondition.WHEN_SEEN) AutoConnectCondition.CASE_OPEN else autoConnectCondition,
            showPopUpOnCaseOpen = showPopUpOnCaseOpen,
            showPopUpOnConnection = showPopUpOnConnection,
            conversationAction = conversationAction,
            conversationVolumeReduction = conversationVolumeReduction,
            notifyWhenCharged = notifyWhenCharged,
            chargedThreshold = chargedThreshold,
            chargedSlotScope = chargedSlotScope,
            notifyWhenCaseLow = notifyWhenCaseLow,
            caseLowThreshold = caseLowThreshold,
        )

    override fun toString(): String = "AppleDeviceProfile(" +
        "id=$id, label=$label, priority=$priority, model=$model, " +
        "minimumSignalQuality=$minimumSignalQuality, " +
        "identityKey=${if (identityKey == null) "null" else "<redacted>"}, " +
        "encryptionKey=${if (encryptionKey == null) "null" else "<redacted>"}, " +
        "address=$address, autoPause=$autoPause, autoPlay=$autoPlay, " +
        "startMusicOnWear=$startMusicOnWear, " +
        "onePodMode=$onePodMode, autoConnect=$autoConnect, " +
        "autoConnectCondition=$autoConnectCondition, " +
        "showPopUpOnCaseOpen=$showPopUpOnCaseOpen, " +
        "showPopUpOnConnection=$showPopUpOnConnection, " +
        "conversationAction=$conversationAction, " +
        "conversationVolumeReduction=$conversationVolumeReduction, " +
        "notifyWhenCharged=$notifyWhenCharged, " +
        "chargedThreshold=$chargedThreshold, " +
        "chargedSlotScope=$chargedSlotScope, " +
        "notifyWhenCaseLow=$notifyWhenCaseLow, " +
        "caseLowThreshold=$caseLowThreshold, " +
        "batteryEstimateEnabled=$batteryEstimateEnabled, " +
        "learnedAllowOffEnabled=$learnedAllowOffEnabled, " +
        "lastRequestedListeningModeCycleMask=$lastRequestedListeningModeCycleMask, " +
        "stemActions=$stemActions" +
        ")"
}