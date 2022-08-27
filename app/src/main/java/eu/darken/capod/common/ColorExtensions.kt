package eu.darken.capod.common

import android.content.Context
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat

fun colorString(context: Context, @ColorRes colorRes: Int, string: String): SpannableString {
    val colored = SpannableString(string)
    colored.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, colorRes)), 0, string.length, 0)
    return colored
}