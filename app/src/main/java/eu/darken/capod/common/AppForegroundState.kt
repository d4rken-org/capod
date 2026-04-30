package eu.darken.capod.common

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppForegroundState @Inject constructor(
    @AppScope appScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) {

    val isForeground: StateFlow<Boolean> = callbackFlow {
        val lifecycle = runCatching { ProcessLifecycleOwner.get().lifecycle }
            .getOrElse {
                log(TAG, WARN) { "Failed to access process lifecycle, assuming background: ${it.asLog()}" }
                trySend(false)
                close()
                return@callbackFlow
            }

        trySend(safeCurrentForegroundState())

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> trySend(true)
                Lifecycle.Event.ON_STOP -> trySend(false)
                else -> Unit
            }
        }

        runCatching { lifecycle.addObserver(observer) }
            .onFailure {
                log(TAG, WARN) { "Failed to observe process lifecycle, assuming background: ${it.asLog()}" }
                trySend(false)
                close()
            }

        awaitClose {
            runCatching { lifecycle.removeObserver(observer) }
                .onFailure { log(TAG, WARN) { "Failed to remove process lifecycle observer: ${it.asLog()}" } }
        }
    }
        .flowOn(dispatcherProvider.MainImmediate)
        .catch {
            log(TAG, WARN) { "Foreground state flow failed, assuming background: ${it.asLog()}" }
            emit(false)
        }
        .distinctUntilChanged()
        .onEach { log(TAG) { "isForeground=$it" } }
        .stateIn(
            scope = appScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = safeCurrentForegroundState(),
        )

    private fun safeCurrentForegroundState(): Boolean = runCatching {
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }.getOrElse {
        log(TAG, WARN) { "Failed to read process lifecycle state, assuming background: ${it.asLog()}" }
        false
    }

    companion object {
        private val TAG = logTag("App", "ForegroundState")
    }
}
