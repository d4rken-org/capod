package eu.darken.capod.main.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.permissions.isRequired
import eu.darken.capod.reaction.settings.ReactionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generalSettings: GeneralSettings,
    private val reactionSettings: ReactionSettings,
) {
    private val permissionCheckTrigger = MutableStateFlow(UUID.randomUUID())

    fun recheck() {
        log(TAG) { "recheck()" }
        permissionCheckTrigger.value = UUID.randomUUID()
    }

    val missingPermissions: Flow<Set<Permission>> = combine(
        permissionCheckTrigger,
        generalSettings.monitorMode.flow,
        reactionSettings.showPopUpOnCaseOpen.flow
    ) { _, monitorMode, showPopUp ->
        Permission.values()
            .filter { it != Permission.IGNORE_BATTERY_OPTIMIZATION || monitorMode == MonitorMode.ALWAYS }
            .filter { it != Permission.ACCESS_BACKGROUND_LOCATION || monitorMode == MonitorMode.ALWAYS }
            .filter { it != Permission.SYSTEM_ALERT_WINDOW || showPopUp }
            .filter { it.isRequired(context) }
            .toSet()
    }
        .onEach { log(TAG) { "Missing permission: $it" } }

    companion object {
        private val TAG = logTag("PermissionTool")
    }
}