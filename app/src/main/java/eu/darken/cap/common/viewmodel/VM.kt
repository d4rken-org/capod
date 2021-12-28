package eu.darken.cap.common.viewmodel

import androidx.annotation.CallSuper
import androidx.lifecycle.ViewModel
import eu.darken.cap.common.debug.logging.log
import eu.darken.cap.common.debug.logging.logTag

abstract class VM : ViewModel() {
    val TAG: String = logTag("VM", javaClass.simpleName)

    init {
        log(TAG) { "Initialized" }
    }

    @CallSuper
    override fun onCleared() {
        log(TAG) { "onCleared()" }
        super.onCleared()
    }
}