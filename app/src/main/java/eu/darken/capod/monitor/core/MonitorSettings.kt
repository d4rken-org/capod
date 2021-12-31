package eu.darken.capod.monitor.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorSettings @Inject constructor() {

    val mode: Mode = Mode.ALWAYS

    enum class Mode(val raw: String) {
        MANUAL("monitor.mode.manual"),
        AUTOMATIC("monitor.mode.automatic"),
        ALWAYS("monitor.mode.always")
    }
}