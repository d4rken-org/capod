package eu.darken.capod.common.preferences

import android.content.Context
import android.content.res.TypedArray
import android.os.Parcelable
import android.util.AttributeSet
import androidx.annotation.PluralsRes
import androidx.preference.DialogPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import eu.darken.capod.R
import eu.darken.capod.common.preferences.PercentSliderPreferenceDialogFragment.Companion.newInstance
import kotlinx.parcelize.Parcelize

class PercentSliderPreference(context: Context?, attrs: AttributeSet?) : DialogPreference(context, attrs) {
    @get:PluralsRes val sliderTextPluralsResource: Int

    val min: Float
    val max: Float

    private var internalValue = 0f
    private var internalValueSet = false

    init {
        val a = getContext().obtainStyledAttributes(attrs, R.styleable.PercentSliderPreference)
        min = a.getFloat(R.styleable.PercentSliderPreference_pspMin, 0f)
        max = a.getFloat(R.styleable.PercentSliderPreference_pspMax, 1f)
        sliderTextPluralsResource = a.getResourceId(R.styleable.PercentSliderPreference_sliderText, 0)
        a.recycle()
    }

    // Always persist/notify the first time.
    var value: Float
        get() = internalValue
        set(value) {
            // Always persist/notify the first time.
            val changed = internalValue != value
            if (changed || !internalValueSet) {
                internalValue = value
                internalValueSet = true
                persistFloat(value)
                if (changed) {
                    notifyChanged()
                }
            }
        }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Int {
        return a.getInteger(index, 0)
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {
        value = if (restoreValue) getPersistedFloat(internalValue) else defaultValue as Float
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        if (isPersistent) {
            // No need to save instance state since it's persistent
            return superState
        }
        return SavedState(
            value = value,
            superState = superState,
        )
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state?.javaClass != SavedState::class.java) {
            // Didn't save state for us in onSaveInstanceState
            return super.onRestoreInstanceState(state)
        }

        val myState = state as SavedState
        super.onRestoreInstanceState(myState.superState)
        value = myState.value
    }

    @Parcelize
    data class SavedState(
        val value: Float,
        val superState: Parcelable,
    ) : Parcelable

    companion object {
        private const val DIALOG_FRAGMENT_TAG = "android.support.v7.preference.PreferenceFragment.DIALOG"
        fun onDisplayPreferenceDialog(preferenceFragment: PreferenceFragmentCompat, preference: Preference): Boolean {
            if (preference is PercentSliderPreference) {
                val fragmentManager = preferenceFragment.fragmentManager
                if (fragmentManager!!.findFragmentByTag(DIALOG_FRAGMENT_TAG) == null) {
                    val dialogFragment = newInstance(preference.getKey())
                    dialogFragment.setTargetFragment(preferenceFragment, 0)
                    dialogFragment.show(fragmentManager, DIALOG_FRAGMENT_TAG)
                }
                return true
            }
            return false
        }
    }
}