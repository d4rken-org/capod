package eu.darken.capod.common.error

import eu.darken.capod.common.livedata.SingleLiveEvent

interface ErrorEventSource {
    val errorEvents: SingleLiveEvent<Throwable>
}