package eu.darken.capod.pods.core

import android.content.Context
import androidx.annotation.ColorRes

interface HasPodStyle {

    val podStyle: PodStyle

    interface PodStyle {
        fun getLabel(context: Context): String

        @ColorRes
        fun getColor(context: Context): Int

        val identifier: String
    }

}