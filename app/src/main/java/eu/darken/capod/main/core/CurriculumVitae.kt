package eu.darken.capod.main.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.debug.logging.Logging.Priority.INFO
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurriculumVitae @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val Context.dataStore by preferencesDataStore(name = "curriculum_vitae")

    private val dataStore: DataStore<Preferences>
        get() = context.dataStore

    // Lifetime Pro-state history: how often the billing grace period had to save this install, and
    // whether/when Pro was actually lost. Written by the gplay UpgradeRepo from FRESH Play data
    // only; surfaced in every debug log recording so billing complaints arrive with context.
    // Raw preference keys (not DataStoreValues): a transition must update state, counter, and
    // timestamp in ONE transaction.
    private val proStateLastKey = stringPreferencesKey("stats.pro.state.last")
    private val proGraceCountKey = intPreferencesKey("stats.pro.grace.count")
    private val proGraceLastKey = longPreferencesKey("stats.pro.grace.last")
    private val proLostCountKey = intPreferencesKey("stats.pro.lost.count")
    private val proLostLastKey = longPreferencesKey("stats.pro.lost.last")

    enum class ProState { PURCHASED, GRACE, FREE }

    data class ProHistory(
        val lastState: ProState?,
        val graceEngagedCount: Int,
        val graceEngagedLast: Instant?,
        val proLostCount: Int,
        val proLostLast: Instant?,
    )

    // Suspend on purpose: the caller's collector is ordered (billing commit order) and a
    // fire-and-forget launch per update could apply rapid transitions out of order.
    suspend fun updateProState(state: ProState) {
        dataStore.edit { prefs ->
            val previous = parseProState(prefs[proStateLastKey])
            if (previous == state) return@edit
            log(TAG, INFO) { "updateProState(): $previous -> $state" }
            val now = Instant.now().toEpochMilli()
            when (proTransitionOf(previous, state)) {
                ProTransition.GRACE_ENGAGED -> {
                    prefs[proGraceCountKey] = (prefs[proGraceCountKey] ?: 0) + 1
                    prefs[proGraceLastKey] = now
                }

                ProTransition.PRO_LOST -> {
                    prefs[proLostCountKey] = (prefs[proLostCountKey] ?: 0) + 1
                    prefs[proLostLastKey] = now
                }

                // First observation (or an unknown/corrupt stored value): baseline only.
                null -> {}
            }
            prefs[proStateLastKey] = state.name
        }
    }

    suspend fun proHistory(): ProHistory {
        val prefs = dataStore.data.first()
        return ProHistory(
            lastState = parseProState(prefs[proStateLastKey]),
            graceEngagedCount = prefs[proGraceCountKey] ?: 0,
            graceEngagedLast = prefs[proGraceLastKey]?.let { Instant.ofEpochMilli(it) },
            proLostCount = prefs[proLostCountKey] ?: 0,
            proLostLast = prefs[proLostLastKey]?.let { Instant.ofEpochMilli(it) },
        )
    }

    internal enum class ProTransition { GRACE_ENGAGED, PRO_LOST }

    companion object {
        internal val TAG = logTag("Debug", "CurriculumVitae")

        // Tolerant of blank/corrupt/future enum names: an unknown stored value must behave like a
        // fresh baseline, not kill the update job or the recorder's history read.
        internal fun parseProState(raw: String?): ProState? =
            raw?.let { r -> ProState.entries.firstOrNull { it.name == r } }

        // Which transitions count: grace only "engages" coming FROM a confirmed purchase, and Pro
        // is only "lost" when a previously Pro-ish state drops to FREE. Everything else (baseline,
        // recovery, unknown previous value) just moves the stored state. Pure and unit-tested.
        internal fun proTransitionOf(previous: ProState?, current: ProState): ProTransition? = when {
            previous == ProState.PURCHASED && current == ProState.GRACE -> ProTransition.GRACE_ENGAGED
            (previous == ProState.PURCHASED || previous == ProState.GRACE) && current == ProState.FREE ->
                ProTransition.PRO_LOST

            else -> null
        }
    }
}
