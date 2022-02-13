package eu.darken.capod.pods.core

import android.content.Context

interface HasStateDetection {

    val state: State

    interface State {
        fun getLabel(context: Context): String
    }
}