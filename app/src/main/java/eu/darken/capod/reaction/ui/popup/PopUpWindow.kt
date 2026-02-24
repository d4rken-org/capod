package eu.darken.capod.reaction.ui.popup

import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.theming.CapodTheme
import eu.darken.capod.main.core.GeneralSettings
import eu.darken.capod.pods.core.PodDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PopUpWindow @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val generalSettings: GeneralSettings,
) {

    private val windowManager = appContext.getSystemService(WINDOW_SERVICE) as WindowManager
    private val layoutParams = WindowManager.LayoutParams(
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
    }

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun show(device: PodDevice) = try {
        log(TAG) { "open()" }
        // Unconditionally clean up any existing lifecycle owner before creating a new one
        lifecycleOwner?.let { existing ->
            existing.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            existing.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            existing.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        if (composeView?.parent != null) {
            log(TAG) { "View already added." }
            windowManager.removeView(composeView)
        }
        composeView = null
        lifecycleOwner = null

        val owner = OverlayLifecycleOwner()
        lifecycleOwner = owner

        val view = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                CapodTheme(state = generalSettings.currentThemeState) {
                    PopUpContent(device = device, onClose = { close() })
                }
            }
        }
        composeView = view

        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager.addView(view, layoutParams)
    } catch (e: Exception) {
        log(TAG, ERROR) { "open() failed: ${e.asLog()}" }
    }

    fun close() = try {
        log(TAG) { "close()" }
        val view = composeView
        if (view?.parent != null) {
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            windowManager.removeView(view)
        } else {
            log(TAG) { "View was not added" }
        }
        composeView = null
        lifecycleOwner = null
    } catch (e: Exception) {
        log(TAG, ERROR) { "close() failed: ${e.asLog()}" }
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
        private val TAG = logTag("Reaction", "PopUp", "Window")
    }
}
