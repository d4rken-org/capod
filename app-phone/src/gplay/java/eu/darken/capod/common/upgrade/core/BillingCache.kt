package eu.darken.capod.common.upgrade.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.preferences.createFlowPreference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val preferences = context.getSharedPreferences("settings_gplay", Context.MODE_PRIVATE)

    val lastProStateAt = preferences.createFlowPreference(
        "gplay.cache.lastProAt",
        0L
    )
}
