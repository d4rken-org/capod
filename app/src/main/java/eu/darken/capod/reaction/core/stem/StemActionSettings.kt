package eu.darken.capod.reaction.core.stem

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.datastore.createValue
import eu.darken.capod.common.serialization.SerializationCapod
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StemActionSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    @SerializationCapod json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_stem_actions")

    private val dataStore: DataStore<Preferences> get() = context.dataStore

    val leftSingle = dataStore.createValue("stem.left.single", StemAction.NONE, json, onErrorFallbackToDefault = true)
    val leftDouble = dataStore.createValue("stem.left.double", StemAction.NONE, json, onErrorFallbackToDefault = true)
    val leftTriple = dataStore.createValue("stem.left.triple", StemAction.NONE, json, onErrorFallbackToDefault = true)
    val leftLong = dataStore.createValue("stem.left.long", StemAction.NONE, json, onErrorFallbackToDefault = true)
    val rightSingle = dataStore.createValue("stem.right.single", StemAction.NONE, json, onErrorFallbackToDefault = true)
    val rightDouble = dataStore.createValue("stem.right.double", StemAction.NONE, json, onErrorFallbackToDefault = true)
    val rightTriple = dataStore.createValue("stem.right.triple", StemAction.NONE, json, onErrorFallbackToDefault = true)
    val rightLong = dataStore.createValue("stem.right.long", StemAction.NONE, json, onErrorFallbackToDefault = true)

    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }
}
