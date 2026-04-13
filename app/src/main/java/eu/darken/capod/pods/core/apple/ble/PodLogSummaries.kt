package eu.darken.capod.pods.core.apple.ble

import eu.darken.capod.common.bluetooth.redactedForLogs
import eu.darken.capod.pods.core.apple.ble.history.KnownDevice
import eu.darken.capod.profiles.core.DeviceProfile
import java.util.Locale

private fun String.shortIdForLogs(): String = take(8)

fun DeviceProfile.logSummary(): String = buildString {
    append("profile(id=")
    append(id.shortIdForLogs())
    append(", model=")
    append(model)
    append(", addr=")
    append(address?.redactedForLogs() ?: "-")
    append(')')
}

fun BlePodSnapshot.logSummary(): String = buildString {
    append("pod(model=")
    append(model)
    append(", id=")
    append(identifier.toString().shortIdForLogs())
    append(", profile=")
    append(meta.profile?.id?.shortIdForLogs() ?: "-")
    append(", addr=")
    append(address.redactedForLogs())
    append(", rssi=")
    append(rssi)
    append(", reliability=")
    append(String.format(Locale.US, "%.2f", reliability))
    append(')')
}

fun KnownDevice.logSummary(): String = buildString {
    append("known(id=")
    append(id.toString().shortIdForLogs())
    append(", model=")
    append(history.last().model)
    append(", history=")
    append(history.size)
    append(", seen=")
    append(seenCounter)
    append(", addr=")
    append(lastAddress.redactedForLogs())
    append(')')
}
