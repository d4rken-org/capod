package eu.darken.capod.common.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import eu.darken.capod.R
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.withinApiLevel

enum class Permission(
    val minApiLevel: Int,
    val maxApiLevel: Int = Int.MAX_VALUE,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val permissionId: String,
    val isGranted: (Context) -> Boolean = {
        ContextCompat.checkSelfPermission(it, permissionId) == PackageManager.PERMISSION_GRANTED
    },
) {
    BLUETOOTH(
        minApiLevel = Build.VERSION_CODES.BASE,
        maxApiLevel = Build.VERSION_CODES.R,
        labelRes = R.string.permission_bluetooth_label,
        descriptionRes = R.string.permission_bluetooth_description,
        permissionId = "android.permission.BLUETOOTH",
    ),
    BLUETOOTH_CONNECT(
        minApiLevel = Build.VERSION_CODES.S,
        labelRes = R.string.permission_bluetooth_connect_label,
        descriptionRes = R.string.permission_bluetooth_connect_description,
        permissionId = "android.permission.BLUETOOTH_CONNECT",
    ),
    BLUETOOTH_SCAN(
        minApiLevel = Build.VERSION_CODES.S,
        labelRes = R.string.permission_bluetooth_scan_label,
        descriptionRes = R.string.permission_bluetooth_scan_description,
        permissionId = "android.permission.BLUETOOTH_SCAN",
    ),
    ACCESS_FINE_LOCATION(
        minApiLevel = Build.VERSION_CODES.BASE,
        maxApiLevel = Build.VERSION_CODES.R,
        labelRes = R.string.permission_access_fine_location_label,
        descriptionRes = R.string.permission_access_fine_location_description,
        permissionId = "android.permission.ACCESS_FINE_LOCATION",
    ),
    ACCESS_BACKGROUND_LOCATION(
        minApiLevel = Build.VERSION_CODES.Q,
        maxApiLevel = Build.VERSION_CODES.R,
        labelRes = R.string.permission_background_location_label,
        descriptionRes = R.string.permission_background_location_description,
        permissionId = "android.permission.ACCESS_BACKGROUND_LOCATION",
    ),
    IGNORE_BATTERY_OPTIMIZATION(
        minApiLevel = Build.VERSION_CODES.BASE,
        labelRes = R.string.permission_ignore_battery_optimizations_label,
        descriptionRes = R.string.permission_ignore_battery_optimizations_description,
        permissionId = "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        isGranted = {
            val pwm = it.getSystemService(Context.POWER_SERVICE) as PowerManager
            pwm.isIgnoringBatteryOptimizations(BuildConfigWrap.APPLICATION_ID)
        },
    )
}

fun Permission.isRequired(context: Context): Boolean = when {
    !withinApiLevel(minApiLevel, maxApiLevel) -> false
    else -> !isGranted(context)
}