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
        ScannerMode.BALANCED,
        moshi
    )

    val autoPause = preferences.createFlowPreference(
        "core.eardetection.autopause.enabled",
        false
    )

    val autoPlay = preferences.createFlowPreference(
        "core.eardetection.autoplay.enabled",
        false
    )

    val showAll = preferences.createFlowPreference(
        "core.showall.enabled",
        false
    )

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper(
        monitorMode,
        scannerMode,
        autoPause,
        autoPlay,
        showAll,
        debugSettings.isAutoReportEnabled,
    )
}