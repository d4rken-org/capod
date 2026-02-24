package eu.darken.capod.main.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.debug.DebugSettings
import eu.darken.capod.common.preferences.PreferenceStoreMapper
import eu.darken.capod.common.preferences.Settings
import eu.darken.capod.common.preferences.createFlowPreference
import eu.darken.capod.common.theming.ThemeColor
import eu.darken.capod.common.theming.ThemeMode
import eu.darken.capod.common.theming.ThemeState
import eu.darken.capod.common.theming.ThemeStyle
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.apple.protocol.IdentityResolvingKey
import eu.darken.capod.pods.core.apple.protocol.ProximityEncryptionKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    debugSettings: DebugSettings,
    moshi: Moshi,
) : Settings() {

    override val preferences: SharedPreferences = context.getSharedPreferences("settings_general", Context.MODE_PRIVATE)

    val monitorMode = preferences.createFlowPreference("core.monitor.mode", MonitorMode.AUTOMATIC, moshi)

    val useExtraMonitorNotification = preferences.createFlowPreference("core.monitor.notification.connected", false)

    val keepConnectedNotificationAfterDisconnect =
        preferences.createFlowPreference("core.monitor.notification.connected.keepafterdisconnected", false)

    val scannerMode = preferences.createFlowPreference("core.scanner.mode", ScannerMode.BALANCED, moshi)

    val oldMinimumSignalQuality = preferences.createFlowPreference("core.signal.minimum", 0.20f)

    val oldMainDeviceAddress = preferences.createFlowPreference<BluetoothAddress?>("core.maindevice.address", null)

    val oldMainDeviceModel = preferences.createFlowPreference("core.maindevice.model", PodDevice.Model.UNKNOWN, moshi)

    val oldMainDeviceIdentityKey = preferences.createFlowPreference<IdentityResolvingKey?>(
        "core.maindevice.identitykey",
        null,
        moshi
    )

    val oldMainDeviceEncryptionKey = preferences.createFlowPreference<ProximityEncryptionKey?>(
        "core.maindevice.encryptionkey",
        null,
        moshi
    )

    val isOffloadedFilteringDisabled = preferences.createFlowPreference(
        "core.compat.offloaded.filtering.disabled",
        false
    )
    val isOffloadedBatchingDisabled = preferences.createFlowPreference("core.compat.offloaded.batching.disabled", false)
    val useIndirectScanResultCallback = preferences.createFlowPreference("core.compat.indirectcallback.enabled", false)

    val isOnboardingDone = preferences.createFlowPreference("core.onboarding.done", false)

    val themeMode = preferences.createFlowPreference(
        "core.ui.theme.mode", ThemeMode.SYSTEM, moshi, onErrorFallbackToDefault = true
    )
    val themeStyle = preferences.createFlowPreference(
        "core.ui.theme.style", ThemeStyle.DEFAULT, moshi, onErrorFallbackToDefault = true
    )
    val themeColor = preferences.createFlowPreference(
        "core.ui.theme.color", ThemeColor.BLUE, moshi, onErrorFallbackToDefault = true
    )

    val currentThemeState: ThemeState
        get() = ThemeState(
            mode = themeMode.value,
            style = themeStyle.value,
            color = themeColor.value,
        )

    val themeState: Flow<ThemeState>
        get() = combine(themeMode.flow, themeStyle.flow, themeColor.flow) { mode, style, color ->
            ThemeState(mode, style, color)
        }

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper(
        monitorMode,
        useExtraMonitorNotification,
        keepConnectedNotificationAfterDisconnect,
        scannerMode,
        isOffloadedFilteringDisabled,
        isOffloadedBatchingDisabled,
        useIndirectScanResultCallback,
        themeMode,
        themeStyle,
        themeColor,
        debugSettings.isAutoReportingEnabled,
    )
}
