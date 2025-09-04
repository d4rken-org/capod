package eu.darken.capod.common.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.SwitchPreferenceCompat
import eu.darken.capod.R

class MaterialSwitchPreference(context: Context, attrs: AttributeSet?) :
    SwitchPreferenceCompat(context, attrs) {

    init {
        // Use material switch
        widgetLayoutResource = R.layout.preference_material_switch
    }
}