package eu.darken.cap.common.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import eu.darken.cap.R

enum class Permission(
    val minApiLevel: Int,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val permissionId: String,
) {
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
    )
}

fun Permission.isGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, permissionId) == PackageManager.PERMISSION_GRANTED
}