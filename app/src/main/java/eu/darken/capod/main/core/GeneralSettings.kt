package eu.darken.capod.main.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.BuildConfigWrap
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.datastore.createValue
import eu.darken.capod.common.serialization.ByteArrayBase64Serializer
import eu.darken.capod.common.serialization.SerializationCapod
import eu.darken.capod.common.theming.ThemeColor
import eu.darken.capod.common.theming.ThemeMode
import eu.darken.capod.common.theming.ThemeStyle
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.ble.protocol.IdentityResolvingKey
import eu.darken.capod.pods.core.apple.ble.protocol.ProximityEncryptionKey
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneralSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    @SerializationCapod json: Json,
) {

    private val Context.dataStore by preferencesDataStore(
        name = "settings_general",
        produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, "settings_general")) }
    )

    private val dataStore: DataStore<Preferences> get() = context.dataStore

    val monitorMode = dataStore.createValue("core.monitor.mode", MonitorMode.AUTOMATIC, json, onErrorFallbackToDefault = true)

    val useExtraMonitorNotification = dataStore.createValue("core.monitor.notification.connected", false)

    val keepConnectedNotificationAfterDisconnect =
        dataStore.createValue("core.monitor.notification.connected.keepafterdisconnected", false)

    val oldMinimumSignalQuality = dataStore.createValue("core.signal.minimum", 0.20f)

    val oldMainDeviceAddress = dataStore.createValue<BluetoothAddress?>(
        key = stringPreferencesKey("core.maindevice.address"),
        reader = { raw -> raw as? String },
        writer = { value -> value },
    )

    val oldMainDeviceModel =
        dataStore.createValue("core.maindevice.model", PodModel.UNKNOWN, json, onErrorFallbackToDefault = true)

    val oldMainDeviceIdentityKey = dataStore.createValue<IdentityResolvingKey?>(
        key = "core.maindevice.identitykey",
        defaultValue = null,
        json = json,
        serializer = ByteArrayBase64Serializer.nullable,
        onErrorFallbackToDefault = true,
    )

    val oldMainDeviceEncryptionKey = dataStore.createValue<ProximityEncryptionKey?>(
        key = "core.maindevice.encryptionkey",
        defaultValue = null,
        json = json,
        serializer = ByteArrayBase64Serializer.nullable,
        onErrorFallbackToDefault = true,
    )

    val isOffloadedFilteringDisabled = dataStore.createValue("core.compat.offloaded.filtering.disabled", false)
    val isOffloadedBatchingDisabled = dataStore.createValue("core.compat.offloaded.batching.disabled", false)
    val useIndirectScanResultCallback = dataStore.createValue("core.compat.indirectcallback.enabled", false)

    val isOnboardingDone = dataStore.createValue("core.onboarding.done", false)

    val themeMode = dataStore.createValue(
        "core.ui.theme.mode", ThemeMode.SYSTEM, json,
        onErrorFallbackToDefault = BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.DEV,
    )
    val themeStyle = dataStore.createValue(
        "core.ui.theme.style", ThemeStyle.DEFAULT, json,
        onErrorFallbackToDefault = BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.DEV,
    )
    val themeColor = dataStore.createValue(
        "core.ui.theme.color", ThemeColor.BLUE, json,
        onErrorFallbackToDefault = BuildConfigWrap.BUILD_TYPE != BuildConfigWrap.BuildType.DEV,
    )
}
