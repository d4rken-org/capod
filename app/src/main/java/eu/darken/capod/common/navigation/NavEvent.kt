package eu.darken.capod.common.navigation

sealed interface NavEvent {
    data class GoTo(
        val destination: NavigationDestination,
        val popUpTo: NavigationDestination? = null,
        val inclusive: Boolean = false,
    ) : NavEvent

    data object Up : NavEvent

    data object Finish : NavEvent
}
