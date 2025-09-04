package eu.darken.capod.profiles.core

import android.os.Parcelable
import eu.darken.capod.common.serialization.NameBasedPolyJsonAdapterFactory
import eu.darken.capod.pods.core.PodDevice

sealed interface DeviceProfile : Parcelable {
    val id: ProfileId
    val label: String
    val priority: Int
    val model: PodDevice.Model
    val minimumSignalQuality: Float?
    val address: String?

    companion object {
        val MOSHI_FACTORY: NameBasedPolyJsonAdapterFactory<DeviceProfile> =
            NameBasedPolyJsonAdapterFactory.of(DeviceProfile::class.java)
                .withSubtype(AppleDeviceProfile::class.java, "identityKey")
    }
}