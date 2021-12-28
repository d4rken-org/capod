package eu.darken.cap.common.error

import eu.darken.cap.common.livedata.SingleLiveEvent

interface ErrorEventSource {
    val errorEvents: SingleLiveEvent<Throwable>
}