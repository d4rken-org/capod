package eu.darken.capod.reaction.ui.popup

import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.theming.CapodTheme
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.main.core.currentThemeState
import eu.darken.capod.monitor.core.PodDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PopUpWindow @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val generalSettings: GeneralSettings,
) {

    private val windowManager = appContext.getSystemService(WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // Below API 30 the overlay window can't extend behind the navigation bar, so the shadow
    // needs its room inside the window.
    private val extraBottomPadding = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 0.dp else 16.dp

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var deviceState: MutableState<PodDevice?>? = null
    private var targetState: MutableState<PopUpTarget>? = null
    private var viewParams: WindowManager.LayoutParams? = null
    private var fallback: Runnable? = null

    private val popUpLifecycle = PopUpLifecycle(WindowEffects())

    @Volatile var isMainActivityVisible: Boolean = false

    fun show(device: PodDevice) {
        if (isMainActivityVisible) {
            log(TAG) { "Suppressing popup, MainActivity is visible" }
            return
        }
        try {
            log(TAG, INFO) { "open()" }
            popUpLifecycle.show(device)
        } catch (e: Exception) {
            log(TAG, ERROR) { "open() failed: ${e.asLog()}" }
        }
    }

    fun close() = try {
        log(TAG, INFO) { "close()" }
        popUpLifecycle.close()
    } catch (e: Exception) {
        log(TAG, ERROR) { "close() failed: ${e.asLog()}" }
    }

    // Fresh params per window, so the NOT_TOUCHABLE flag of an exit can't carry over to the next popup.
    private fun createLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        val dm = appContext.resources.displayMetrics
        val margin = (24 * dm.density).toInt()
        width = minOf(dm.widthPixels - margin * 2, (400 * dm.density).toInt())
        y = 0
        title = "CAPod popup"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
        }
    }

    private inner class WindowEffects : PopUpLifecycle.Effects<PodDevice> {

        override fun attach(payload: PodDevice, target: PopUpTarget) {
            val device = mutableStateOf<PodDevice?>(payload)
            deviceState = device
            val targetHolder = mutableStateOf(target)
            targetState = targetHolder

            val owner = OverlayLifecycleOwner()
            lifecycleOwner = owner

            val view = ComposeView(appContext).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    val currentDevice = device.value
                    if (currentDevice != null) {
                        CapodTheme(state = generalSettings.currentThemeState) {
                            PopUpOverlay(
                                device = currentDevice,
                                target = targetHolder.value,
                                onClose = { close() },
                                // Posted, so the window isn't removed from inside its own composition.
                                onExitFinished = { token -> mainHandler.post { popUpLifecycle.onExitFinished(token) } },
                                extraBottomPadding = extraBottomPadding,
                            )
                        }
                    }
                }
            }
            composeView = view

            owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

            val params = createLayoutParams()
            viewParams = params
            windowManager.addView(view, params)
        }

        override fun update(payload: PodDevice) {
            deviceState?.value = payload
        }

        override fun setTarget(target: PopUpTarget) {
            targetState?.value = target
        }

        override fun setTouchable(touchable: Boolean) {
            val view = composeView ?: return
            val params = viewParams ?: return
            params.flags = if (touchable) {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            windowManager.updateViewLayout(view, params)
        }

        override fun scheduleFallback(token: Long) {
            cancelFallback()
            val runnable = Runnable {
                fallback = null
                popUpLifecycle.onFallback(token)
            }
            fallback = runnable
            mainHandler.postDelayed(runnable, PopUpOverlayState.EXIT_DURATION_MS + EXIT_FALLBACK_MARGIN_MS)
        }

        override fun cancelFallback() {
            fallback?.let { mainHandler.removeCallbacks(it) }
            fallback = null
        }

        override fun destroyHost() {
            lifecycleOwner?.let { existing ->
                existing.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                existing.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                existing.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
        }

        override fun removeWindow() {
            val view = composeView ?: return
            if (view.parent != null) windowManager.removeView(view)
        }

        override fun release() {
            composeView = null
            lifecycleOwner = null
            deviceState = null
            targetState = null
            viewParams = null
        }
    }

    /**
     * Minimal [SavedStateRegistryOwner] for hosting a [ComposeView] inside a WindowManager overlay,
     * where no Activity or Fragment lifecycle is available.
     */
    private class OverlayLifecycleOwner : SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        init {
            savedStateRegistryController.performRestore(null)
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }
    }

    companion object {
        private const val EXIT_FALLBACK_MARGIN_MS = 1000L
        private val TAG = logTag("Reaction", "PopUp", "Window")
    }
}
