package eu.darken.capod.monitor.core.aap

import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton

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
) {
    fun start() {
        log(TAG) { "start()" }
        merge(
            aapAutoConnect.monitor(),
            aapKeyPersister.monitor(),
            aapLearnedSettingsPersister.monitor(),
            stemConfigSender.monitor(),
            stemPressReaction.monitor(),
        )
            .catch { e -> log(TAG, WARN) { "AAP lifecycle error: ${e.asLog()}" } }
            .setupCommonEventHandlers(TAG) { "aapActive" }
            .launchIn(appScope)
    }

    companion object {
        private val TAG = logTag("Monitor", "AapLifecycleManager")
    }
}
