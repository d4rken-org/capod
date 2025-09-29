package eu.darken.capod.profiles.core

import kotlinx.coroutines.flow.first

suspend fun DeviceProfilesRepo.currentProfiles(): List<DeviceProfile> = profiles.first()