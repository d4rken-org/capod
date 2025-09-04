package eu.darken.capod.devices.core

import android.os.Parcelable
import com.squareup.moshi.JsonClass
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.IdentityResolvingKey
import eu.darken.capod.pods.core.apple.protocol.ProximityEncryptionKey
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
@JsonClass(generateAdapter = true)
data class DeviceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: BluetoothAddress? = null,
    val model: PodDevice.Model = PodDevice.Model.UNKNOWN,
    val identityKey: IdentityResolvingKey? = null,
    val encryptionKey: ProximityEncryptionKey? = null,
    val minimumSignalQuality: Float = 0.20f,
    val isEnabled: Boolean = true
) : Parcelable