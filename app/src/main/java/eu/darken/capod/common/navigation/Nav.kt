package eu.darken.capod.common.navigation

import kotlinx.serialization.Serializable

object Nav {
    sealed interface Main : NavigationDestination {
        @Serializable
        data object Overview : Main

        @Serializable
        data object Onboarding : Main

        @Serializable
        data object DeviceManager : Main

        @Serializable
        data class DeviceProfileCreation(val profileId: String? = null) : Main

        @Serializable
        data object TroubleShooter : Main

        @Serializable
        data object Upgrade : Main

        @Serializable
        data class DeviceSettings(val profileId: String) : Main

        @Serializable
        data class PressControls(val profileId: String) : Main
    }

    sealed interface Settings : NavigationDestination {
        @Serializable
        data object Index : Settings

        @Serializable
        data object General : Settings

        @Serializable
        data object Support : Settings

        @Serializable
        data object Acknowledgements : Settings

        @Serializable
        data object ContactSupport : Settings
    }
}
