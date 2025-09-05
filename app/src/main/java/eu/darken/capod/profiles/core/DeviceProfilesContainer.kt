package eu.darken.capod.profiles.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceProfilesContainer(
    @Json(name = "profiles") val profiles: List<DeviceProfile> = emptyList()
)