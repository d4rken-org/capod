package eu.darken.capod.main.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.androidstarter.common.preferences.Settings
import eu.darken.capod.common.debug.autoreport.DebugSettings
import eu.darken.capod.common.preferences.PreferenceStoreMapper
import eu.darken.capod.common.preferences.createFlowPreference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    debugSettings: DebugSettings,
    moshi: Moshi,
) : Settings() {

    override val preferences: SharedPreferences = context.getSharedPreferences("settings_general", Context.MODE_PRIVATE)

    val monitorMode = preferences.createFlowPreference(
        "core.monitor.mode",
        MonitorMode.AUTOMATIC,
        moshi
    )

    val scannerMode = preferences.createFlowPreference(
        "core.scanner.mode",
        ScannerMode.LOW_POWER,
        moshi
    )

    val showAll = preferences.createFlowPreference(
        "core.showall.enabled",
        false
    )

    val minimumSignalQuality = preferences.createFlowPreference(
        "core.signal.minimum",
        0.25f
    )

    val mainDeviceAddress = preferences.createFlowPreference<String?>(
        "core.maindevice.address",
        null
    )

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper(
        monitorMode,
        scannerMode,
        showAll,
        minimumSignalQuality,
        mainDeviceAddress,
        debugSettings.isAutoReportEnabled,
    )
}