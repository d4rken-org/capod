package eu.darken.capod.common.preferences

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.VERTICAL
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceDialogFragmentCompat
import eu.darken.capod.common.UIConverter
import kotlin.math.roundToInt

class PercentSliderPreferenceDialogFragment : PreferenceDialogFragmentCompat(), SeekBar.OnSeekBarChangeListener {
    private val layoutContainer by lazy { LinearLayout(requireContext()) }
    private val valueText by lazy { TextView(requireContext()) }
    private val splashText by lazy { TextView(requireContext()) }
    private val seekBar by lazy { SeekBar(requireContext()) }

    private val preferencePercent: PercentSliderPreference
        get() = super.getPreference() as PercentSliderPreference

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        layoutContainer.apply {
            orientation = VERTICAL
            val px: Int = UIConverter.convertDpToPixels(requireContext(), 24f)
            setPadding(px, 0, px, 0)
        }

        splashText.apply {
            if (preferencePercent.dialogMessage != null) splashText.text = preferencePercent.dialogMessage
            layoutContainer.addView(this)
        }

        valueText.apply {
            gravity = Gravity.CENTER_HORIZONTAL
            textSize = 32f

            layoutContainer.addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }

        seekBar.apply {
            setOnSeekBarChangeListener(this@PercentSliderPreferenceDialogFragment)
            max = (preferencePercent.max * 100).roundToInt()
            progress = (preferencePercent.value * 100).roundToInt()
            layoutContainer.addView(this, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }

        updateValueText()
        builder.setView(layoutContainer)
        builder.setNegativeButton(null, null)
        super.onPrepareDialogBuilder(builder)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult) return
        val value: Int = seekBar.progress
        if (preferencePercent.callChangeListener(value)) {
            preferencePercent.value = value / 100f
        }
    }

    override fun onProgressChanged(seek: SeekBar, value: Int, fromTouch: Boolean) {
        if (value < preferencePercent.min) {
            seek.progress = (preferencePercent.min * 100).roundToInt()
        }
        updateValueText()
    }

    private fun updateValueText() {
        val count: Int = seekBar.progress
        valueText.text = if (preferencePercent.sliderTextPluralsResource != 0) {
            resources.getQuantityString(preferencePercent.sliderTextPluralsResource, count, count)
        } else {
            "$count%"
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {}
    override fun onStopTrackingTouch(seekBar: SeekBar) {}

    companion object {
        @JvmStatic fun newInstance(key: String): PercentSliderPreferenceDialogFragment {
            val fragment = PercentSliderPreferenceDialogFragment()
            val arguments = Bundle()
            arguments.putString(ARG_KEY, key)
            fragment.arguments = arguments
            return fragment
        }
    }
}