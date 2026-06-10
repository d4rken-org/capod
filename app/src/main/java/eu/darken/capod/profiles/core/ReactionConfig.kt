package eu.darken.capod.profiles.core

import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import eu.darken.capod.reaction.core.charged.ChargedSlotScope
import eu.darken.capod.reaction.core.conversation.ConversationAction

data class ReactionConfig(
    val autoPause: Boolean = false,
    val autoPlay: Boolean = false,
    val startMusicOnWear: Boolean = false,
    val onePodMode: Boolean = false,
    val autoConnect: Boolean = false,
    val autoConnectCondition: AutoConnectCondition = AutoConnectCondition.WHEN_SEEN,
    val showPopUpOnCaseOpen: Boolean = false,
    val showPopUpOnConnection: Boolean = false,
    val conversationAction: ConversationAction = ConversationAction.NOTHING,
    /** Percentage to lower media volume by when [conversationAction] is LOWER_VOLUME (clamped 10..90 on use). */
    val conversationVolumeReduction: Int = DEFAULT_CONVERSATION_VOLUME_REDUCTION,
    val notifyWhenCharged: Boolean = false,
    /** Battery percentage at which the charged notification fires (clamped 50..100 on use). */
    val chargedThreshold: Int = DEFAULT_CHARGED_THRESHOLD,
    val chargedSlotScope: ChargedSlotScope = ChargedSlotScope.PODS_AND_CASE,
) {
    companion object {
        const val DEFAULT_CHARGED_THRESHOLD = 100
        const val MIN_CHARGED_THRESHOLD = 50
        const val MAX_CHARGED_THRESHOLD = 100
        const val CHARGED_THRESHOLD_STEP = 10

        const val DEFAULT_CONVERSATION_VOLUME_REDUCTION = 50
        const val MIN_CONVERSATION_VOLUME_REDUCTION = 10

        /** 100% lowers media volume all the way to 0 — i.e. full mute while speaking. */
        const val MAX_CONVERSATION_VOLUME_REDUCTION = 100
    }
}

interface HasReactionConfig {
    val reactionConfig: ReactionConfig
}

fun DeviceProfile?.toReactionConfig(): ReactionConfig =
    (this as? HasReactionConfig)?.reactionConfig ?: ReactionConfig()
