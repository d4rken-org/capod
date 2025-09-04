package eu.darken.capod.devices.core

import android.os.Parcelable
import eu.darken.capod.common.serialization.NameBasedPolyJsonAdapterFactory
import eu.darken.capod.pods.core.PodDevice
import kotlin.jvm.java

sealed interface DeviceProfile : Parcelable {
    val id: ProfileId
    val name: String
    val minimumSignalQuality: Float
    val isEnabled: Boolean
    val model: PodDevice.Model
    val priority: Int

    companion object {
        val MOSHI_FACTORY: NameBasedPolyJsonAdapterFactory<DeviceProfile> =
            NameBasedPolyJsonAdapterFactory.of(DeviceProfile::class.java)
                .withSubtype(AppleDeviceProfile::class.java, "identityKey")
    }
}