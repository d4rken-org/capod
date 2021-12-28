package eu.darken.cap.common.smart

import androidx.navigation.NavDirections
import eu.darken.cap.common.coroutine.DispatcherProvider
import eu.darken.cap.common.debug.logging.asLog
import eu.darken.cap.common.debug.logging.log
import eu.darken.cap.common.error.ErrorEventSource
import eu.darken.cap.common.flow.setupCommonEventHandlers
import eu.darken.cap.common.livedata.SingleLiveEvent
import eu.darken.cap.common.navigation.NavEventSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn


abstract class Smart2VM(
    dispatcherProvider: DispatcherProvider,
) : SmartVM(dispatcherProvider), NavEventSource, ErrorEventSource {

    override val navEvents = SingleLiveEvent<NavDirections?>()
    override val errorEvents = SingleLiveEvent<Throwable>()

    init {
        launchErrorHandler = CoroutineExceptionHandler { _, ex ->
            log(TAG) { "Error during launch: ${ex.asLog()}" }
            errorEvents.postValue(ex)
        }
    }

    override fun <T> Flow<T>.launchInViewModel() = this
        .setupCommonEventHandlers(TAG) { "launchInViewModel()" }
        .launchIn(vmScope)

}