package eu.darken.capod.main.core

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.permissions.isRequired
import javax.inject.Inject

@Reusable
class PermissionTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generalSettings: GeneralSettings,
) {

    suspend fun missingPermissions(): Set<Permission> = Permission.values()
        .filter { it != Permission.IGNORE_BATTERY_OPTIMIZATION || generalSettings.monitorMode.value == MonitorMode.ALWAYS }
        .filter { it.isRequired(context) }
        .toSet()
}