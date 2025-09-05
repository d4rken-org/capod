package eu.darken.capod.profiles.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.preferences.PreferenceStoreMapper
import eu.darken.capod.common.preferences.Settings
import eu.darken.capod.common.preferences.createFlowPreference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceProfilesSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) : Settings() {

    override val preferences: SharedPreferences = context.getSharedPreferences("device_profiles", Context.MODE_PRIVATE)

    val profiles = preferences.createFlowPreference<DeviceProfilesContainer>(
        "profiles",
        DeviceProfilesContainer(),
        moshi
    )

    override val preferenceDataStore: PreferenceDataStore = PreferenceStoreMapper()
}