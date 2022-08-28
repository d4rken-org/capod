package eu.darken.capod.reaction.ui.popup

import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.R
import eu.darken.capod.common.coroutine.DispatcherProvider
import eu.darken.capod.common.debug.logging.Logging.Priority.ERROR
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PopUpWindow @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val podViewFactory: PopUpPodViewFactory,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val context = ContextThemeWrapper(appContext, R.style.AppTheme)
    private val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,  // Display it on top of other application windows
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,  // Don't let it grab the input focus
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,  // Make the underlying application window visible

        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM
    }
    private val popUpView: View = layoutInflater.inflate(R.layout.popup_window_container_layout, null).apply {
        findViewById<View>(R.id.close_action).setOnClickListener { close() }
    }
    private val deviceContainer = popUpView.findViewById<FrameLayout>(R.id.popup_content)

    fun show(device: PodDevice) = try {
        log(TAG) { "open()" }
        if (popUpView.windowToken != null || popUpView.parent != null) {
            log(TAG) { "View already added." }
            close()
        }

        val podView = podViewFactory.createContentView(deviceContainer, device)
        deviceContainer.removeAllViews()
        deviceContainer.addView(podView)
        windowManager.addView(popUpView, layoutParams)
    } catch (e: Exception) {
        log(TAG, ERROR) { "open() failed: ${e.asLog()}" }
    }


    fun close() = try {
        log(TAG) { "close()" }
        if (popUpView.parent != null) {
            windowManager.removeView(popUpView)
            deviceContainer.removeAllViews()
        } else {
            log(TAG) { "View was not added" }
        }
    } catch (e: Exception) {
        log(TAG, ERROR) { "close() failed: ${e.asLog()}" }
    }

    companion object {
        private val TAG = logTag("Reaction", "PopUp", "Window")
    }
}