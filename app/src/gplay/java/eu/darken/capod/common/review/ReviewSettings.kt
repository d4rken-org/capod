package eu.darken.capod.common.review

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.capod.common.datastore.createValue
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.common.serialization.InstantEpochMillisSerializer
import eu.darken.capod.common.serialization.SerializationCapod
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewSettings @Inject constructor(
    @ApplicationContext private val context: Context,
    @SerializationCapod json: Json,
) {

    private val Context.dataStore by preferencesDataStore(name = "settings_review_gplay")

    val dataStore: DataStore<Preferences>
        get() = context.dataStore

    // Explicit serializer: `java.time.Instant` has no `@Serializable` companion, so the reified
    // `serializer<T>()` the inline overload uses cannot resolve one for it.
    val lastDismissed = dataStore.createValue(
        key = "review.dismissedAt",
        defaultValue = null as Instant?,
        json = json,
        serializer = InstantEpochMillisSerializer.nullable,
    )
    val reviewedAt = dataStore.createValue(
        key = "review.reviewedAt",
        defaultValue = null as Instant?,
        json = json,
        serializer = InstantEpochMillisSerializer.nullable,
    )

    companion object {
        internal val TAG = logTag("Review", "Settings", "Gplay")
    }
}
