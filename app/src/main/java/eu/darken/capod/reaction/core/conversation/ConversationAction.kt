package eu.darken.capod.reaction.core.conversation

import androidx.annotation.StringRes
import eu.darken.capod.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What CAPod does when the pods report that the wearer started speaking (Conversational
 * Awareness). On Android the firmware does not duck audio on its own, so CAPod performs the
 * reaction itself. The action is reverted when speaking stops.
 *
 * Extend by adding entries — the persisted [SerialName] strings are stable identifiers.
 */
@Serializable
enum class ConversationAction(
    val identifier: String,
    @StringRes val labelRes: Int,
) {
    @SerialName("conversation.action.nothing")
    NOTHING("conversation.action.nothing", R.string.settings_conversation_action_nothing_label),

    @SerialName("conversation.action.lower_volume")
    LOWER_VOLUME("conversation.action.lower_volume", R.string.settings_conversation_action_lower_volume_label),

    @SerialName("conversation.action.pause")
    PAUSE("conversation.action.pause", R.string.settings_conversation_action_pause_label),
}
