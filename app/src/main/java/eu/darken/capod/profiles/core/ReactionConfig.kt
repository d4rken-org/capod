package eu.darken.capod.profiles.core

import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition

data class ReactionConfig(
    val autoPause: Boolean = false,
    val autoPlay: Boolean = false,
    val onePodMode: Boolean = false,
    val autoConnect: Boolean = false,
    val autoConnectCondition: AutoConnectCondition = AutoConnectCondition.WHEN_SEEN,
    val showPopUpOnCaseOpen: Boolean = false,
    val showPopUpOnConnection: Boolean = false,
)

interface HasReactionConfig {
    val reactionConfig: ReactionConfig
}

fun DeviceProfile?.toReactionConfig(): ReactionConfig =
    (this as? HasReactionConfig)?.reactionConfig ?: ReactionConfig()
