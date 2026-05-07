package eu.darken.capod.common.bluetooth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NudgeAvailability {
    @SerialName("nudge.unknown") UNKNOWN,
    @SerialName("nudge.available") AVAILABLE,
    @SerialName("nudge.broken") BROKEN,
}

enum class NudgeAttemptResult {
    Accepted,
    Rejected,
    UnavailableHiddenApi,
    UnavailableMissingPermission,
}
