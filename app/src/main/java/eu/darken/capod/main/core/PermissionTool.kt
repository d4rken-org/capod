package eu.darken.capod.main.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.common.permissions.isRequired
import eu.darken.capod.monitor.core.MonitorModeResolver
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.toReactionConfig
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import eu.darken.capod.reaction.core.stem.StemAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val monitorModeResolver: MonitorModeResolver,
    private val profilesRepo: DeviceProfilesRepo,
) {
    private val permissionCheckTrigger = MutableStateFlow(UUID.randomUUID())

    fun recheck() {
        log(TAG) { "recheck()" }
        permissionCheckTrigger.value = UUID.randomUUID()
    }

    private val overlayNeeded: Flow<Boolean> = profilesRepo.profiles
        .map { needsOverlay(it) }
        .distinctUntilChanged()

    val missingPermissions: Flow<Set<Permission>> = combine(
        permissionCheckTrigger,
        monitorModeResolver.effectiveMode,
        overlayNeeded,
    ) { _, monitorMode, overlay ->
        Permission.entries
            .filter { isApplicable(it, monitorMode, overlay) }
            .filter { it.isRequired(context) }
            .toSet()
    }
        .distinctUntilChanged()
        .onEach { missing ->
            if (missing.isNotEmpty()) log(TAG) { "Missing permissions: $missing" }
        }

    val missingScanPermissions: Flow<Set<Permission>> = missingPermissions
        .map { perms -> perms.filter { it.isScanBlocking }.toSet() }

    companion object {
        private val TAG = logTag("PermissionTool")

        /**
         * Whether [permission] is applicable for the user's current configuration.
         * Three permissions are conditional on monitor mode or overlay usage:
         *  - [Permission.IGNORE_BATTERY_OPTIMIZATION] only when always-on scanning is needed
         *  - [Permission.ACCESS_BACKGROUND_LOCATION] same
         *  - [Permission.SYSTEM_ALERT_WINDOW] only when [needsOverlay] is true for the profiles
         * Everything else is unconditionally applicable.
         */
        internal fun isApplicable(
            permission: Permission,
            monitorMode: MonitorMode,
            overlayNeeded: Boolean,
        ): Boolean = when (permission) {
            Permission.IGNORE_BATTERY_OPTIMIZATION,
            Permission.ACCESS_BACKGROUND_LOCATION -> monitorMode == MonitorMode.ALWAYS
            Permission.SYSTEM_ALERT_WINDOW -> overlayNeeded
            else -> true
        }

        /** A popup reaction is enabled, or a press is mapped to [StemAction.VoiceAssistant]. */
        internal fun needsOverlay(profiles: List<DeviceProfile>): Boolean = profiles.any { profile ->
            val popup = profile.toReactionConfig().let { it.showPopUpOnCaseOpen || it.showPopUpOnConnection }
            popup || (profile is AppleDeviceProfile && profile.stemActions.uses(StemAction.VoiceAssistant))
        }
    }
}
