package eu.darken.capod.common.uix

import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.error.ErrorEventSource2
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.navigation.NavEvent
import eu.darken.capod.common.navigation.NavigationDestination
import eu.darken.capod.common.navigation.NavigationEventSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn

/**
 * Base ViewModel for Compose screens.
 * Provides error events via [SingleEventFlow] and navigation via [NavigationEventSource].
 *
 * Compose render state should be exposed as VM-owned [StateFlow]s. Those render-state flows must
 * stay collector-safe and never throw into `collectAsStateWithLifecycle()`. Use [safeStateIn] to
 * forward recoverable failures to [errorEvents] and emit an explicit fallback UI state instead.
 */
abstract class ViewModel4(
    dispatcherProvider: DispatcherProvider,
) : ViewModel2(dispatcherProvider), NavigationEventSource, ErrorEventSource2 {

    override val navEvents = SingleEventFlow<NavEvent>()
    override val errorEvents = SingleEventFlow<Throwable>()

    init {
        launchErrorHandler = CoroutineExceptionHandler { _, ex ->
            log(TAG) { "Error during launch: ${ex.asLog()}" }
            errorEvents.emitBlocking(ex)
        }
    }

    override fun <T> Flow<T>.launchInViewModel() = this
        .setupCommonEventHandlers(TAG) { "launchInViewModel()" }
        .launchIn(vmScope)

    fun navTo(
        destination: NavigationDestination,
        popUpTo: NavigationDestination? = null,
        inclusive: Boolean = false,
    ) {
        log(TAG) { "navTo($destination)" }
        navEvents.tryEmit(NavEvent.GoTo(destination, popUpTo, inclusive))
    }

    fun navUp() {
        log(TAG) { "navUp()" }
        navEvents.tryEmit(NavEvent.Up)
    }

    /**
     * Collect a render-state flow in [vmScope] and convert upstream failures into explicit fallback
     * UI state plus an [errorEvents] emission. Cancellation is never converted into UI state.
     */
    protected fun <T> Flow<T>.safeStateIn(
        initialValue: T,
        started: SharingStarted = SharingStarted.WhileSubscribed(5000),
        onError: (Throwable) -> T,
    ): StateFlow<T> = this
        .catch { ex ->
            if (ex is CancellationException) throw ex

            log(TAG, WARN) { "Error during state collection: ${ex.asLog()}" }
            errorEvents.emit(ex)
            emit(onError(ex))
        }
        .stateIn(
            scope = vmScope,
            started = started,
            initialValue = initialValue,
        )
}
