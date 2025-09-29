package eu.darken.capod.profiles.core

import android.os.Parcelable
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import eu.darken.capod.pods.core.PodDevice

sealed interface DeviceProfile : Parcelable {
    val id: ProfileId
    val label: String
    val priority: Int
    val model: PodDevice.Model
    val minimumSignalQuality: Float?
    val address: String?

    companion object {
        const val DEFAULT_MINIMUM_SIGNAL_QUALITY = 0.15f
        
        val MOSHI_FACTORY = PolymorphicJsonAdapterFactory.of(DeviceProfile::class.java, "type")
            .withSubtype(AppleDeviceProfile::class.java, "apple")
    }
}