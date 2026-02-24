package eu.darken.capod.common.uix

import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.error.ErrorEventSource2
import eu.darken.capod.common.flow.SingleEventFlow
import eu.darken.capod.common.flow.setupCommonEventHandlers
import eu.darken.capod.common.navigation.NavEvent
import eu.darken.capod.common.navigation.NavigationDestination
import eu.darken.capod.common.navigation.NavigationEventSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn

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
}
