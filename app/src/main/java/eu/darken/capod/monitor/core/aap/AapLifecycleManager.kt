package eu.darken.capod.monitor.core.aap

import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.permissions.Permission
import eu.darken.capod.main.core.PermissionTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Launches AAP auto-connect and key persistence in [appScope],
 * independent of [eu.darken.capod.monitor.core.worker.MonitorService] lifecycle.
 *
 * Call [start] from [eu.darken.capod.monitor.core.DeviceMonitor] init to activate.
 */
@Singleton
class AapLifecycleManager @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val aapAutoConnect: AapAutoConnect,
    private val aapKeyPersister: AapKeyPersister,
    private val aapLearnedSettingsPersister: AapLearnedSettingsPersister,
    private val stemConfigSender: StemConfigSender,
    private val stemPressReaction: StemPressReaction,
    private val permissionTool: PermissionTool,
) {
    fun start() {
        log(TAG) { "start()" }
        permissionTool.missingPermissions
            .map { Permission.BLUETOOTH_CONNECT in it }
            .distinctUntilChanged()
            .flatMapLatest { missingConnect ->
                if (missingConnect) {
                    log(TAG) { "AAP lifecycle paused: BLUETOOTH_CONNECT missing" }
                    emptyFlow()
                } else {
                    log(TAG) { "AAP lifecycle active: BLUETOOTH_CONNECT granted" }
                    merge(
                        aapAutoConnect.monitor(),
                        aapKeyPersister.monitor(),
                        aapLearnedSettingsPersister.monitor(),
                        stemConfigSender.monitor(),
                        stemPressReaction.monitor(),
                    )
                        .retryWhen { cause, attempt ->
                            log(TAG, WARN) { "AAP lifecycle error (attempt ${attempt + 1}): ${cause.asLog()}" }
                            delay((1_000L * (attempt + 1).coerceAtMost(5)).milliseconds)
                            true
                        }
                }
            }
            .setupCommonEventHandlers(TAG) { "aapActive" }
            .launchIn(appScope)
    }

    companion object {
        private val TAG = logTag("Monitor", "AapLifecycleManager")
    }
}
