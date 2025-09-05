package eu.darken.capod.profiles.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.IdentityResolvingKey
import eu.darken.capod.pods.core.apple.protocol.ProximityEncryptionKey
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
@JsonClass(generateAdapter = true)
data class AppleDeviceProfile(
    @Json(name = "id") override val id: ProfileId = UUID.randomUUID().toString(),
    @Json(name = "label") override val label: String,
    @Json(name = "priority") override val priority: Int = 0,
    @Json(name = "model") override val model: PodDevice.Model = PodDevice.Model.UNKNOWN,
    @Json(name = "minimumSignalQuality") override val minimumSignalQuality: Float? = DeviceProfile.DEFAULT_MINIMUM_SIGNAL_QUALITY,
    @Json(name = "identityKey") val identityKey: IdentityResolvingKey? = null,
    @Json(name = "encryptionKey") val encryptionKey: ProximityEncryptionKey? = null,
    @Json(name = "address") override val address: String? = null,
) : DeviceProfile