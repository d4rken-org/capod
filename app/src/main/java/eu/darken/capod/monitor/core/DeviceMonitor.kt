package eu.darken.capod.monitor.core

import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.coroutine.AppScope
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.replayingShare
import eu.darken.capod.monitor.core.aap.AapLifecycleManager
import eu.darken.capod.monitor.core.ble.BlePodMonitor
import eu.darken.capod.monitor.core.cache.DeviceStateCache
import eu.darken.capod.monitor.core.cache.toCachedState
import eu.darken.capod.pods.core.apple.PodModel
import eu.darken.capod.pods.core.apple.aap.AapConnectionManager
import eu.darken.capod.pods.core.apple.ble.devices.ApplePods
import eu.darken.capod.pods.core.apple.aap.AapPodState
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.toReactionConfig
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single merge point: combines BLE scan data ([eu.darken.capod.monitor.core.ble.BlePodMonitor]) with AAP connection data
 * ([AapConnectionManager]) and cached device state ([eu.darken.capod.monitor.core.cache.DeviceStateCache]) into unified [PodDevice] objects.
 *
 * Includes cached-only devices for profiles that have cached state but no live BLE data.
 * Persists live device state to [eu.darken.capod.monitor.core.cache.DeviceStateCache] as a side effect.
 * ViewModels should observe [devices] instead of accessing BlePodMonitor directly.
 */
@Singleton
class DeviceMonitor @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val blePodMonitor: BlePodMonitor,
    private val aapManager: AapConnectionManager,
    private val deviceStateCache: DeviceStateCache,
    private val profilesRepo: DeviceProfilesRepo,
    private val aapLifecycleManager: AapLifecycleManager,
) {
    init {
        aapLifecycleManager.start()
    }

    val devices: Flow<List<PodDevice>> = combine(
        blePodMonitor.devices,
        aapManager.allStates,
        deviceStateCache.cachedStates,
        profilesRepo.profiles,
    ) { pods, aapStates, cachedStates, profiles ->
        // Live devices — BLE + AAP + cached fallback for missing fields
        val liveDevices = pods.map { pod ->
            val profile = pod.meta.profile
            PodDevice(
                profileId = profile?.id,
                label = profile?.label,
                ble = pod,
                aap = aapStates.forProfile(profile),
                cached = profile?.id?.let { cachedStates[it] },
                profileAddress = profile?.address,
                profileModel = profile?.model,
                profileKeyState = profile.toBleKeyState(),
                reactions = profile.toReactionConfig(),
            )
        }

        // Collapse live duplicates sharing an identity-backed profile — e.g. when the legacy
        // signal-quality fallback misattributed ambient strangers to our profile.
        // Only applied when the group has at least one IRK-verified candidate; for no-IRK
        // profiles, multiple hits are genuinely different devices and must all remain visible.
        val profileDedupedLive = run {
            val anonymous = liveDevices.filter { it.profileId == null }
            val byProfile = liveDevices
                .filter { it.profileId != null }
                .groupBy { it.profileId!! }
                .flatMap { (_, group) ->
                    val hasIrkMatch = group.any { (it.ble as? ApplePods)?.meta?.isIRKMatch == true }
                    if (!hasIrkMatch || group.size == 1) {
                        group
                    } else {
                        listOf(
                            group.sortedWith(
                                compareByDescending<PodDevice> { (it.ble as? ApplePods)?.meta?.isIRKMatch == true }
                                    .thenByDescending { it.signalQuality }
                            ).first()
                        )
                    }
                }
            byProfile + anonymous
        }

        // Non-live devices — profiles with no live BLE detection. Synthesize from cache
        // and/or AAP, whichever is available:
        //   - cache only           → "cached-only" case (PR #484 fixed AAP attach here)
        //   - AAP only             → cold-start case: AAP connected but no battery message
        //                            received yet, so no cache write has happened
        //   - cache + AAP          → mid-session BLE staleness with AAP still alive
        val liveProfileIds = profileDedupedLive.mapNotNull { it.profileId }.toSet()
        val nonLiveDevices = profiles
            .filter { it.id !in liveProfileIds }
            .mapNotNull { profile ->
                val cached = cachedStates[profile.id]
                val aap = aapStates.forProfile(profile)
                if (cached == null && aap == null) return@mapNotNull null
                PodDevice(
                    profileId = profile.id,
                    label = profile.label,
                    ble = null,
                    aap = aap,
                    cached = cached,
                    profileAddress = profile.address,
                    profileModel = profile.model,
                    profileKeyState = profile.toBleKeyState(),
                    reactions = profile.toReactionConfig(),
                )
            }

        // De-dupe: if a synthesized non-live device with active AAP shares its model with
        // an anonymous (profileId == null) BLE pod, the anonymous pod is most likely the
        // same physical AirPods — the IRK match failed (e.g. wrong identity key, or keys
        // haven't propagated yet). Hide the anonymous BLE pod to avoid two cards for the
        // same device. Best-effort: model match only — BLE addresses are RPAs and can't
        // be matched directly.
        val nonLiveAapModels = nonLiveDevices
            .filter { it.aap != null }
            .map { it.model }
            .filter { it != PodModel.UNKNOWN }
            .toSet()
        val dedupedLiveDevices = if (nonLiveAapModels.isEmpty()) {
            profileDedupedLive
        } else {
            val seenAnonymousModels = mutableSetOf<PodModel>()
            profileDedupedLive.filter { device ->
                val isAnonymous = device.profileId == null
                val matchesNonLive = device.model in nonLiveAapModels
                // Hide at most one anonymous BLE pod per non-live AAP model so a second
                // physical device of the same model isn't accidentally hidden.
                !(isAnonymous && matchesNonLive && seenAnonymousModels.add(device.model))
            }
        }

        dedupedLiveDevices + nonLiveDevices
    }
        .onEach { devices -> persistLiveDevices(devices) }
        .replayingShare(appScope)

    private suspend fun persistLiveDevices(devices: List<PodDevice>) {
        for (device in devices) {
            val profileId = device.profileId ?: continue
            val existing = deviceStateCache.cachedStates.value[profileId]
            val newState = device.toCachedState(existing) ?: continue

            log(TAG, VERBOSE) { "Persisting state for $profileId" }
            deviceStateCache.save(profileId, newState)
        }
    }

    suspend fun getDeviceForProfile(profileId: String): PodDevice? {
        log(TAG) { "getDeviceForProfile(profileId=$profileId)" }

        val liveDevice = devices.firstOrNull()?.firstOrNull { it.profileId == profileId }
        if (liveDevice != null) {
            log(TAG) { "Found live device for profile $profileId" }
            return liveDevice
        }

        val profile = profilesRepo.profiles.firstOrNull()?.firstOrNull { it.id == profileId }
        if (profile == null) {
            log(TAG) { "No profile with id $profileId" }
            return null
        }
        val cached = deviceStateCache.load(profileId)
        val aap = aapManager.allStates.value.forProfile(profile)

        log(TAG) {
            "Synthesizing device for profile $profileId (cached=${cached != null}, aap=${aap != null})"
        }
        return PodDevice(
            profileId = profileId,
            label = profile.label,
            ble = null,
            aap = aap,
            cached = cached,
            profileAddress = profile.address,
            profileModel = profile.model,
            profileKeyState = profile.toBleKeyState(),
            reactions = profile.toReactionConfig(),
        )
    }

    /**
     * Single source of truth for "find the AAP state that belongs to this profile".
     * AAP is keyed by the bonded BR/EDR address, which lives on [DeviceProfile.address].
     * Used by both the live and cached-only branches of [devices] so they cannot drift apart.
     */
    private fun Map<BluetoothAddress, AapPodState>.forProfile(profile: DeviceProfile?): AapPodState? =
        profile?.address?.let { this[it] }

    /**
     * Derives the stable badge key state from the profile's stored IRK/ENC. Used so the badge
     * icons don't evaporate every time the BLE scanner misses a scan batch.
     */
    private fun DeviceProfile?.toBleKeyState(): BleKeyState {
        val apple = this as? AppleDeviceProfile ?: return BleKeyState.NONE
        if (apple.identityKey == null) return BleKeyState.NONE
        return if (apple.encryptionKey != null) BleKeyState.IRK_AND_ENCRYPTED else BleKeyState.IRK_ONLY
    }

    companion object {
        private val TAG = logTag("DeviceMonitor")
    }
}
