package eu.darken.capod.common.upgrade.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.datastore.createValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val Context.dataStore by preferencesDataStore(
        name = "settings_gplay",
        produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, "settings_gplay")) }
    )

    private val dataStore: DataStore<Preferences> get() = context.dataStore

    val lastProStateAt = dataStore.createValue(
        "gplay.cache.lastProAt",
        0L
    )

    // SKU id of the last confirmed Pro purchase — determines which grace window applies.
    // Empty for legacy installs that were Pro before this field existed.
    val lastProStateSku = dataStore.createValue(
        "gplay.cache.lastProSku",
        ""
    )
}
