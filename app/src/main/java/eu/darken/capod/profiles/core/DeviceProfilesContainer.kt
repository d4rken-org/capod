package eu.darken.capod.profiles.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceProfilesContainer(
    @SerialName("profiles") val profiles: List<DeviceProfile> = emptyList()
)