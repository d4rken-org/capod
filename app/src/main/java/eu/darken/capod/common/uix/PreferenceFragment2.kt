package eu.darken.capod.common.uix

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.annotation.XmlRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.preference.PreferenceFragmentCompat
import androidx.viewbinding.ViewBinding
import eu.darken.capod.common.preferences.Settings
import eu.darken.capod.main.ui.settings.SettingsFragment

abstract class PreferenceFragment2
    : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    abstract val settings: Settings

    @get:XmlRes
    abstract val preferenceFile: Int

    val toolbar: Toolbar
        get() = (parentFragment as SettingsFragment).toolbar

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        toolbar.menu.clear()
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = settings.preferenceDataStore
        settings.preferences.registerOnSharedPreferenceChangeListener(this)
        refreshPreferenceScreen()
    }

    override fun onDestroy() {
        settings.preferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun getCallbackFragment(): Fragment? = parentFragment

    fun refreshPreferenceScreen() {
        if (preferenceScreen != null) preferenceScreen = null
        addPreferencesFromResource(preferenceFile)
        onPreferencesCreated()
    }

    open fun onPreferencesCreated() {

    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {

    }

    fun setupMenu(@MenuRes menuResId: Int, block: (MenuItem) -> Unit) {
        toolbar.apply {
            menu.clear()
            inflateMenu(menuResId)
            setOnMenuItemClickListener {
                block(it)
                true
            }
        }
    }

    inline fun <T> LiveData<T>.observe2(
        crossinline callback: (T) -> Unit
    ) {
        observe(viewLifecycleOwner) { callback.invoke(it) }
    }

    inline fun <T, reified VB : ViewBinding?> LiveData<T>.observe2(
        ui: VB,
        crossinline callback: VB.(T) -> Unit
    ) {
        observe(viewLifecycleOwner) { callback.invoke(ui, it) }
    }
}