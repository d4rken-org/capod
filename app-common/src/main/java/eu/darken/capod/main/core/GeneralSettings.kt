package eu.darken.capod.main.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.debug.autoreport.DebugSettings
import eu.darken.capod.common.preferences.PreferenceStoreMapper
import eu.darken.capod.common.preferences.Settings
import eu.darken.capod.common.preferences.createFlowPreference
import eu.darken.capod.pods.core.PodDevice
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
    val scannerMode = preferences.createFlowPreference("core.scanner.mode", ScannerMode.BALANCED, moshi)

    val showAll = preferences.createFlowPreference("core.showall.enabled", false)

    val minimumSignalQuality = preferences.createFlowPreference("core.signal.minimum", 0.25f)

    val mainDeviceAddress = preferences.createFlowPreference<String?>("core.maindevice.address", null)
    val mainDeviceModel = preferences.createFlowPreference("core.maindevice.model", PodDevice.Model.UNKNOWN, moshi)

    val isOffloadedFilteringDisabled =
        preferences.createFlowPreference("core.compat.offloaded.filtering.disabled", false)
    val isOffloadedBatchingDisabled = preferences.createFlowPreference("core.compat.offloaded.batching.disabled", false)

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper(
        monitorMode,
        scannerMode,
        showAll,
        minimumSignalQuality,
        mainDeviceAddress,
        isOffloadedFilteringDisabled,
        isOffloadedBatchingDisabled,
        debugSettings.isAutoReportingEnabled,
    )
}