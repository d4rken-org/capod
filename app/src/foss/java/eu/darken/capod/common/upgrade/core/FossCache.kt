package eu.darken.capod.common.upgrade.core

import android.content.Context
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.preferences.createFlowPreference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FossCache @Inject constructor(
    @ApplicationContext context: Context,
    moshi: Moshi
) {

    private val preferences = context.getSharedPreferences("settings_foss", Context.MODE_PRIVATE)

    val upgrade = preferences.createFlowPreference<FossUpgrade?>(
        key = "foss.upgrade",
        moshi = moshi,
        defaultValue = null,
    )

}