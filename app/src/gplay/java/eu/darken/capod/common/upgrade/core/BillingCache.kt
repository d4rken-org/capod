package eu.darken.capod.common.upgrade.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.datastore.createValue
import javax.inject.Inject
import javax.inject.Singleton

private val Context.gplayDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings_gplay",
    produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, "settings_gplay")) }
)

@Singleton
class BillingCache internal constructor(
    private val dataStore: DataStore<Preferences>,
) {

    @Inject constructor(@ApplicationContext context: Context) : this(context.gplayDataStore)

    val lastProStateAt = dataStore.createValue(KEY_LAST_PRO_AT.name, 0L)

    // SKU id of the last confirmed Pro purchase — determines which grace window applies.
    // Empty for legacy installs that were Pro before this field existed.
    val lastProStateSku = dataStore.createValue(KEY_LAST_PRO_SKU.name, "")

    // Start of the current "fresh data can't confirm Pro" episode, 0 = no open episode. Drives
    // the two-stage grace UI (calm confirmation phase first, diagnostics once the episode ages).
    val proUnconfirmedAt = dataStore.createValue(KEY_PRO_UNCONFIRMED_AT.name, 0L)

    // One transaction: a confirmed Pro purchase stamps the anchor (SKU only when the caller wants
    // to move it) and atomically closes any unconfirmed episode. Observers and crash recovery
    // must never see the anchor updated but the episode still open, or vice versa.
    suspend fun stampLastProState(skuId: String?, at: Long) {
        dataStore.edit { prefs ->
            skuId?.let { prefs[KEY_LAST_PRO_SKU] = it }
            prefs[KEY_LAST_PRO_AT] = at
            prefs[KEY_PRO_UNCONFIRMED_AT] = 0L
        }
    }

    // Starts the unconfirmed episode clock. Set-if-unset: follow-up failures must not push the
    // diagnostics threshold out. An episode only exists relative to a previous confirmation, and
    // a stored stamp from before that confirmation or from the future is corrupt state that gets
    // repaired instead of trusted.
    suspend fun recordProUnconfirmed(at: Long) {
        dataStore.edit { prefs ->
            val lastProAt = prefs[KEY_LAST_PRO_AT] ?: 0L
            // Also rejects failures arriving moments after a confirmation: a confirmation and a
            // conflicting empty snapshot within the same minute is emission reordering around a
            // racing purchase event, not a real unconfirmed state.
            if (lastProAt <= 0L || at - lastProAt < MIN_CONFIRMATION_AGE_MS) return@edit
            val current = prefs[KEY_PRO_UNCONFIRMED_AT] ?: 0L
            val corrupt = current != 0L && (current <= lastProAt || current > at)
            if (current == 0L || corrupt) prefs[KEY_PRO_UNCONFIRMED_AT] = at
        }
    }

    companion object {
        private val KEY_LAST_PRO_AT = longPreferencesKey("gplay.cache.lastProAt")
        private val KEY_LAST_PRO_SKU = stringPreferencesKey("gplay.cache.lastProSku")
        private val KEY_PRO_UNCONFIRMED_AT = longPreferencesKey("gplay.cache.proUnconfirmedAt")
        internal const val MIN_CONFIRMATION_AGE_MS = 60_000L
    }
}
