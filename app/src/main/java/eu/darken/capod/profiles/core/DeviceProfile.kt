package eu.darken.capod.profiles.core

import android.os.Parcelable
import eu.darken.capod.pods.core.PodModel
import kotlinx.serialization.Serializable

@Serializable
sealed interface DeviceProfile : Parcelable {
    val id: ProfileId
    val label: String
    val priority: Int
    val model: PodModel
    val minimumSignalQuality: Float?
    val address: String?

    companion object {
        const val DEFAULT_MINIMUM_SIGNAL_QUALITY = 0.15f
    }
}