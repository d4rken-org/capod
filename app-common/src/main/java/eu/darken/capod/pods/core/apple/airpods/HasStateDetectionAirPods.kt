package eu.darken.capod.pods.core.apple.airpods

import android.content.Context
import androidx.annotation.StringRes
import eu.darken.capod.common.R
import eu.darken.capod.pods.core.HasStateDetection
import eu.darken.capod.pods.core.apple.ApplePods

interface HasStateDetectionAirPods : HasStateDetection, ApplePods {

    override val state: ConnectionState
        get() = ConnectionState.values().firstOrNull { rawSuffix == it.raw } ?: ConnectionState.UNKNOWN

    enum class ConnectionState(val raw: UByte?, @StringRes val labelRes: Int) : HasStateDetection.State {
        DISCONNECTED(0x00, R.string.pods_connection_state_disconnected_label),
        IDLE(0x04, R.string.pods_connection_state_idle_label),
        MUSIC(0x05, R.string.pods_connection_state_music_label),
        CALL(0x06, R.string.pods_connection_state_call_label),
        RINGING(0x07, R.string.pods_connection_state_ringing_label),
        HANGING_UP(0x09, R.string.pods_connection_state_hanging_up_label),
        UNKNOWN(null, R.string.pods_connection_state_unknown_label);

        override fun getLabel(context: Context): String = context.getString(labelRes)

        constructor(raw: Int, @StringRes labelRes: Int) : this(raw.toUByte(), labelRes)
    }
}