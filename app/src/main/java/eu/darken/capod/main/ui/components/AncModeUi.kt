package eu.darken.capod.main.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.DoNotDisturbOn
import androidx.compose.material.icons.twotone.Headphones
import androidx.compose.material.icons.twotone.Hearing
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.capod.R
import eu.darken.capod.pods.core.apple.aap.protocol.AapSetting

@StringRes
fun AapSetting.AncMode.Value.shortLabelRes(): Int = when (this) {
    AapSetting.AncMode.Value.OFF -> R.string.anc_mode_off
    AapSetting.AncMode.Value.ON -> R.string.anc_mode_on
    AapSetting.AncMode.Value.TRANSPARENCY -> R.string.anc_mode_transparency
    AapSetting.AncMode.Value.ADAPTIVE -> R.string.anc_mode_adaptive
}

fun AapSetting.AncMode.Value.shortLabel(context: Context): String = context.getString(shortLabelRes())

fun AapSetting.AncMode.Value.icon(): ImageVector = when (this) {
    AapSetting.AncMode.Value.OFF -> Icons.TwoTone.DoNotDisturbOn
    AapSetting.AncMode.Value.ON -> Icons.TwoTone.Headphones
    AapSetting.AncMode.Value.TRANSPARENCY -> Icons.TwoTone.Hearing
    AapSetting.AncMode.Value.ADAPTIVE -> Icons.TwoTone.AutoAwesome
}

