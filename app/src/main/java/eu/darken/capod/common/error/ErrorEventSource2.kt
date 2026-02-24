package eu.darken.capod.common.error

import eu.darken.capod.common.flow.SingleEventFlow

interface ErrorEventSource2 {
    val errorEvents: SingleEventFlow<Throwable>
}
