package eu.darken.capod.main.ui.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.SystemClock
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.capod.R
import eu.darken.capod.common.bluetooth.BluetoothAddress
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.flow.throttleLatest
import eu.darken.capod.common.hasApiLevel
import eu.darken.capod.main.ui.MainActivity
import eu.darken.capod.main.ui.components.iconDrawableRes
import eu.darken.capod.main.ui.components.shortLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Quick Settings tile that cycles ANC modes for the user-perceived primary AirPods.
 * Mirrors [eu.darken.capod.main.ui.widget.AncGlanceWidget] but renders into the system
 * QS panel instead of a home-screen widget.
 */
@AndroidEntryPoint
class AncTileService : TileService() {

    @Inject lateinit var dispatcherProvider: DispatcherProvider
    @Inject lateinit var sendCoordinator: AncTileSendCoordinator
    @Inject lateinit var stateStore: AncTileStateStore

    private var listenScope: CoroutineScope? = null
    private val instanceId = Integer.toHexString(System.identityHashCode(this))

    override fun onCreate() {
        super.onCreate()
        log(TAG, VERBOSE) { "onCreate(instance=$instanceId, sinceDestroy=${elapsedSinceLastDestroy()})" }
    }

    override fun onTileAdded() {
        log(TAG, VERBOSE) { "onTileAdded(instance=$instanceId)" }
        // Render a static placeholder so we never block SystemUI's bind path on a
        // first-emission DataStore/Billing read. onStartListening will fill in real state.
        renderTile(AncTileState.Connecting)
    }

    override fun onStartListening() {
        log(TAG, VERBOSE) { "onStartListening(instance=$instanceId, sinceDestroy=${elapsedSinceLastDestroy()})" }
        listenScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + dispatcherProvider.Default)
        listenScope = scope
        scope.launch {
            stateStore.state.throttleLatest(250).collect { state ->
                log(TAG, VERBOSE) { "collector: state=$state" }
                withContext(dispatcherProvider.Main) { renderTile(state) }
            }
        }
    }

    override fun onStopListening() {
        log(TAG, VERBOSE) { "onStopListening(instance=$instanceId)" }
        listenScope?.cancel()
        listenScope = null
    }

    override fun onDestroy() {
        val now = SystemClock.elapsedRealtime()
        log(TAG, VERBOSE) { "onDestroy(instance=$instanceId)" }
        lastDestroyAt = now
        listenScope?.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        log(TAG, VERBOSE) { "onClick(instance=$instanceId, sinceDestroy=${elapsedSinceLastDestroy()}) received tap" }
        val state = stateStore.currentState()
        log(TAG, VERBOSE) {
            "onClick: resolved from state store=$state"
        }
        dispatchClick(state)
    }

    private fun dispatchClick(state: AncTileState) {
        log(TAG, VERBOSE) { "dispatchClick($state)" }
        when (state) {
            AncTileState.NotPro,
            AncTileState.PermissionRequired -> openMainActivityWithUpgrade()
            is AncTileState.Active -> sendNextMode(state)
            AncTileState.BluetoothOff,
            AncTileState.NoDevice,
            AncTileState.NoAncSupport,
            AncTileState.NotConnected,
            AncTileState.Connecting -> {
                // Tile state is STATE_UNAVAILABLE; system shouldn't deliver clicks here,
                // but defensively no-op so we don't crash on unexpected delivery.
            }
        }
    }

    private fun sendNextMode(state: AncTileState.Active) {
        val nextMode = pickNextMode(state.visible, state.current, state.pending)
        log(TAG, VERBOSE) {
            "sendNextMode: visible=${state.visible} current=${state.current} pending=${state.pending} -> next=$nextMode"
        }
        if (nextMode == null || nextMode == (state.pending ?: state.current)) {
            log(TAG, VERBOSE) { "sendNextMode: no advance possible (visible=${state.visible})" }
            return
        }
        val address: BluetoothAddress = state.deviceAddress ?: run {
            log(TAG, WARN) { "sendNextMode: state has no device address" }
            return
        }

        sendCoordinator.scheduleSetAncMode(address, nextMode, 1.seconds)

        val optimisticState = state.copy(pending = nextMode)
        renderTile(optimisticState)
    }

    private fun openMainActivityWithUpgrade() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO_UPGRADE, true)
        }
        if (hasApiLevel(34)) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun renderTile(state: AncTileState) {
        val tile = qsTile ?: run {
            log(TAG, VERBOSE) { "renderTile: skipped, qsTile is null (state=$state)" }
            return
        }
        log(TAG, VERBOSE) { "renderTile: $state" }
        val baseLabel = getString(R.string.tile_anc_label)

        val (subtitle, iconRes, tileState) = when (state) {
            AncTileState.NotPro -> Triple(
                getString(R.string.common_upgrade_required_label),
                R.drawable.ic_anc_off,
                Tile.STATE_INACTIVE,
            )
            AncTileState.PermissionRequired -> Triple(
                getString(R.string.tile_anc_subtitle_permission_required),
                R.drawable.ic_anc_off,
                Tile.STATE_INACTIVE,
            )
            AncTileState.BluetoothOff -> Triple(
                getString(R.string.tile_anc_subtitle_bluetooth_off),
                R.drawable.ic_anc_off,
                Tile.STATE_UNAVAILABLE,
            )
            AncTileState.NoDevice -> Triple(
                getString(R.string.tile_anc_subtitle_no_device),
                R.drawable.ic_anc_off,
                Tile.STATE_UNAVAILABLE,
            )
            AncTileState.NoAncSupport -> Triple(
                getString(R.string.tile_anc_subtitle_no_anc_support),
                R.drawable.ic_anc_off,
                Tile.STATE_UNAVAILABLE,
            )
            AncTileState.NotConnected -> Triple(
                getString(R.string.anc_widget_aap_not_connected_label),
                R.drawable.ic_anc_off,
                Tile.STATE_UNAVAILABLE,
            )
            AncTileState.Connecting -> Triple(
                getString(R.string.anc_widget_aap_connecting_label),
                R.drawable.ic_anc_off,
                Tile.STATE_UNAVAILABLE,
            )
            is AncTileState.Active -> {
                val displayMode = state.pending?.takeIf { it in state.visible } ?: state.current
                Triple(
                    displayMode.shortLabel(this),
                    displayMode.iconDrawableRes(),
                    Tile.STATE_ACTIVE,
                )
            }
        }

        tile.icon = Icon.createWithResource(this, iconRes)
        tile.state = tileState
        if (hasApiLevel(29)) {
            tile.label = baseLabel
            tile.subtitle = subtitle
        } else {
            // Pre-API 29 tiles can't show a subtitle; fold it into the label.
            tile.label = "$baseLabel · $subtitle"
        }
        if (hasApiLevel(30)) {
            tile.stateDescription = subtitle
        }
        tile.updateTile()
    }

    companion object {
        private val TAG = logTag("Tile", "Anc")
        @Volatile private var lastDestroyAt: Long? = null

        private fun elapsedSinceLastDestroy(): String {
            val destroyedAt = lastDestroyAt ?: return "n/a"
            return "${SystemClock.elapsedRealtime() - destroyedAt}ms"
        }
    }
}
