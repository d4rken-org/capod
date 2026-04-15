package eu.darken.capod.main.ui.devicesettings.cards

sealed interface DeviceDetailItem {
    data class Single(val label: String, val value: String) : DeviceDetailItem
    data class Paired(val start: Single, val end: Single) : DeviceDetailItem
}
