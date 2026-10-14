package eu.darken.capod.main.ui.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.monitor.core.PodDevice
import eu.darken.capod.monitor.core.battery.BatteryEstimate
import eu.darken.capod.profiles.core.ProfileId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class WidgetInputs(
    /** `null` only if the initial read failed and the config flow has not emitted yet. */
    val config: WidgetConfig?,
    val isPro: Boolean,
    val profileLabel: String?,
    val device: PodDevice?,
    val rawEstimate: BatteryEstimate?,
)

private data class ProfileInputs(
    val profileLabel: String?,
    val device: PodDevice?,
    val rawEstimate: BatteryEstimate?,
)

/**
 * Glance keeps the content session alive and neither restarts provideGlance() nor re-runs content
 * for every update(), so config, pro status, profile label and device are observed here rather than
 * read once; a saved config then recomposes a live session. Pass a widget-key-deduped device flow,
 * not `DeviceMonitor.devices`, or every BLE advertisement recomposes the widget.
 *
 * The `initial*` reads are blocking one-shots, run once per session (or per profile id).
 * Profile-dependent state is keyed on the profile id, so switching profiles never pairs the new
 * config with the previous profile's device, label or estimate.
 */
@Composable
internal fun rememberWidgetInputs(
    configFlow: Flow<WidgetConfig>,
    initialConfig: () -> WidgetConfig?,
    proFlow: Flow<Boolean>,
    initialPro: () -> Boolean,
    labelFlow: (ProfileId) -> Flow<String?>,
    initialLabel: (ProfileId) -> String?,
    deviceFlow: (ProfileId) -> Flow<PodDevice?>,
    initialDevice: (ProfileId) -> PodDevice?,
    estimateFlow: ((ProfileId) -> Flow<BatteryEstimate?>)? = null,
    retryDelay: Duration = 2.seconds,
): WidgetInputs {
    val config by remember(configFlow) { configFlow.retryLogged("config", retryDelay) }
        .collectAsState(initial = remember { initialConfig() })
    val isPro by remember(proFlow) { proFlow.retryLogged("isPro", retryDelay) }
        .collectAsState(initial = remember { initialPro() })

    val profileId = config?.profileId
    val profileInputs = key(profileId) {
        if (profileId == null) {
            ProfileInputs(profileLabel = null, device = null, rawEstimate = null)
        } else {
            val profileLabel by remember { labelFlow(profileId).retryLogged("label", retryDelay) }
                .collectAsState(initial = remember { initialLabel(profileId) })
            val device by remember { deviceFlow(profileId).retryLogged("device", retryDelay) }
                .collectAsState(initial = remember { initialDevice(profileId) })
            val rawEstimate = estimateFlow?.let { factory ->
                val estimate by remember { factory(profileId).retryLogged("estimate", retryDelay) }
                    .collectAsState(initial = null)
                estimate
            }
            ProfileInputs(profileLabel = profileLabel, device = device, rawEstimate = rawEstimate)
        }
    }

    return WidgetInputs(
        config = config,
        isPro = isPro,
        profileLabel = profileInputs.profileLabel,
        device = profileInputs.device,
        rawEstimate = profileInputs.rawEstimate,
    )
}

private fun <T> Flow<T>.retryLogged(name: String, retryDelay: Duration): Flow<T> = retryWhen { cause, attempt ->
    if (cause is CancellationException) return@retryWhen false
    log(TAG, ERROR) { "$name flow failed (attempt=$attempt), retrying in $retryDelay: ${cause.asLog()}" }
    delay(retryDelay)
    true
}

private val TAG = logTag("Widget", "Inputs")
