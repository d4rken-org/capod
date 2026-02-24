package eu.darken.capod.common.navigation

import eu.darken.capod.common.flow.SingleEventFlow

interface NavigationEventSource {
    val navEvents: SingleEventFlow<NavEvent>
}
