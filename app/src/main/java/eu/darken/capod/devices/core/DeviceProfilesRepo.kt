package eu.darken.capod.devices.core

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceProfilesRepo @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val moshi: Moshi,
) {

    private val preferences: SharedPreferences = context.getSharedPreferences("device_profiles", Context.MODE_PRIVATE)

    private val _profiles = MutableStateFlow<List<DeviceProfile>>(emptyList())
    val profiles: Flow<List<DeviceProfile>> = _profiles.asStateFlow()

    private val listType = Types.newParameterizedType(List::class.java, DeviceProfile::class.java)
    private val adapter = moshi.adapter<List<DeviceProfile>>(listType)

    init {
        loadProfiles()
    }

    private fun loadProfiles() {
        val json = preferences.getString(KEY_PROFILES, null)
        val loadedProfiles = if (json != null) {
            try {
                adapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                log(VERBOSE) { "Failed to load device profiles: $e" }
                emptyList()
            }
        } else {
            emptyList()
        }
        _profiles.value = loadedProfiles
        log(VERBOSE) { "Loaded ${loadedProfiles.size} device profiles" }
    }

    private fun saveProfiles() {
        val json = adapter.toJson(_profiles.value)
        preferences.edit().putString(KEY_PROFILES, json).apply()
        log(VERBOSE) { "Saved ${_profiles.value.size} device profiles" }
    }

    fun addProfile(profile: DeviceProfile) {
        val updatedProfiles = _profiles.value.toMutableList()
        updatedProfiles.add(profile)
        _profiles.value = updatedProfiles
        saveProfiles()
    }

    fun updateProfile(profile: DeviceProfile) {
        val updatedProfiles = _profiles.value.toMutableList()
        val index = updatedProfiles.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            updatedProfiles[index] = profile
            _profiles.value = updatedProfiles
            saveProfiles()
        }
    }

    fun removeProfile(profileId: String) {
        val updatedProfiles = _profiles.value.toMutableList()
        updatedProfiles.removeAll { it.id == profileId }
        _profiles.value = updatedProfiles
        saveProfiles()
    }

    fun getProfile(profileId: String): DeviceProfile? {
        return _profiles.value.find { it.id == profileId }
    }

    companion object {
        private const val KEY_PROFILES = "profiles"
    }
}