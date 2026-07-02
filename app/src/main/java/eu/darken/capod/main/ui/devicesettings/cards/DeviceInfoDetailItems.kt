package eu.darken.capod.main.ui.devicesettings.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.darken.capod.R
import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo
import java.time.Instant

@Composable
internal fun rememberDeviceInfoDetailLabels() = DeviceInfoDetailLabels(
    manufacturer = stringResource(R.string.device_settings_info_manufacturer_label),
    hardware = stringResource(R.string.device_settings_info_hardware_label),
    serial = stringResource(R.string.device_settings_info_serial_label),
    firmware = stringResource(R.string.device_settings_info_firmware_label),
    firmwarePending = stringResource(R.string.device_settings_info_firmware_pending_label),
    build = stringResource(R.string.device_settings_info_build_label),
    leftSerial = stringResource(R.string.device_settings_info_left_serial_label),
    rightSerial = stringResource(R.string.device_settings_info_right_serial_label),
    leftBonded = stringResource(R.string.device_settings_info_left_bonded_label),
    rightBonded = stringResource(R.string.device_settings_info_right_bonded_label),
    batteryHealth = stringResource(R.string.device_settings_info_battery_health_label),
    leftBatteryHealth = stringResource(R.string.device_settings_info_battery_health_left_label),
    rightBatteryHealth = stringResource(R.string.device_settings_info_battery_health_right_label),
    caseBatteryHealth = stringResource(R.string.device_settings_info_battery_health_case_label),
)

internal data class DeviceInfoDetailLabels(
    val manufacturer: String,
    val hardware: String,
    val serial: String,
    val firmware: String,
    val firmwarePending: String,
    val build: String,
    val leftSerial: String,
    val rightSerial: String,
    val leftBonded: String,
    val rightBonded: String,
    val batteryHealth: String,
    val leftBatteryHealth: String,
    val rightBatteryHealth: String,
    val caseBatteryHealth: String,
)

/**
 * Pre-formatted per-pod health values ("~85%") for the info sheet; null slots are omitted.
 * [pending] is a placeholder shown under the health label while NO value exists yet — the feature
 * stays discoverable while the estimator is still accumulating listening sessions.
 */
internal data class BatteryHealthTexts(
    val left: String? = null,
    val right: String? = null,
    val headset: String? = null,
    val case: String? = null,
    val pending: String? = null,
)

private fun healthItems(health: BatteryHealthTexts?, labels: DeviceInfoDetailLabels): List<DeviceDetailItem> {
    if (health == null) return emptyList()
    return buildList {
        when {
            health.left != null && health.right != null -> add(
                DeviceDetailItem.Paired(
                    start = DeviceDetailItem.Single(labels.leftBatteryHealth, health.left),
                    end = DeviceDetailItem.Single(labels.rightBatteryHealth, health.right),
                )
            )
            health.left != null -> add(DeviceDetailItem.Single(labels.leftBatteryHealth, health.left))
            health.right != null -> add(DeviceDetailItem.Single(labels.rightBatteryHealth, health.right))
        }
        health.headset?.let { add(DeviceDetailItem.Single(labels.batteryHealth, it)) }
        health.case?.let { add(DeviceDetailItem.Single(labels.caseBatteryHealth, it)) }
        if (isEmpty() && health.pending != null) {
            add(DeviceDetailItem.Single(labels.batteryHealth, health.pending))
        }
    }
}

internal fun buildDeviceInfoDetailItems(
    info: AapDeviceInfo?,
    labels: DeviceInfoDetailLabels,
    batteryHealth: BatteryHealthTexts? = null,
    formatDate: (Instant) -> String,
): List<DeviceDetailItem> {
    // Battery health is derived locally, so it's available (and shows the info button) even for
    // BLE-only devices that never produce an AAP device-info response.
    if (info == null) return healthItems(batteryHealth, labels)
    return buildList {
        info.manufacturer.takeIf { it.isNotBlank() }?.let {
            add(DeviceDetailItem.Single(labels.manufacturer, it))
        }
        info.hardwareVersion?.takeIf { it.isNotBlank() }?.let {
            add(DeviceDetailItem.Single(labels.hardware, it))
        }
        info.serialNumber.takeIf { it.isNotBlank() }?.let {
            add(DeviceDetailItem.Single(labels.serial, it))
        }
        info.firmwareVersion.takeIf { it.isNotBlank() }?.let {
            add(DeviceDetailItem.Single(labels.firmware, it))
        }
        info.firmwareVersionPending?.takeIf { it.isNotBlank() }?.let {
            add(DeviceDetailItem.Single(labels.firmwarePending, it))
        }
        info.marketingVersion?.takeIf { it.isNotBlank() }?.let {
            add(DeviceDetailItem.Single(labels.build, it))
        }
        val leftSerial = info.leftEarbudSerial?.takeIf { it.isNotBlank() }
        val rightSerial = info.rightEarbudSerial?.takeIf { it.isNotBlank() }
        when {
            leftSerial != null && rightSerial != null -> add(
                DeviceDetailItem.Paired(
                    start = DeviceDetailItem.Single(labels.leftSerial, leftSerial),
                    end = DeviceDetailItem.Single(labels.rightSerial, rightSerial),
                )
            )
            leftSerial != null -> add(DeviceDetailItem.Single(labels.leftSerial, leftSerial))
            rightSerial != null -> add(DeviceDetailItem.Single(labels.rightSerial, rightSerial))
        }
        val leftBonded = info.leftEarbudFirstPaired?.let(formatDate)
        val rightBonded = info.rightEarbudFirstPaired?.let(formatDate)
        when {
            leftBonded != null && rightBonded != null -> add(
                DeviceDetailItem.Paired(
                    start = DeviceDetailItem.Single(labels.leftBonded, leftBonded),
                    end = DeviceDetailItem.Single(labels.rightBonded, rightBonded),
                )
            )
            leftBonded != null -> add(DeviceDetailItem.Single(labels.leftBonded, leftBonded))
            rightBonded != null -> add(DeviceDetailItem.Single(labels.rightBonded, rightBonded))
        }
        addAll(healthItems(batteryHealth, labels))
    }
}
