package eu.darken.capod.common.bluetooth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.capod.common.datastore.DataStoreValue
import eu.darken.capod.common.datastore.createValue
import eu.darken.capod.common.serialization.SerializationCapod
import kotlinx.serialization.json.Json
import javax.inject.Singleton

private val Context.bluetoothDataStore: DataStore<Preferences> by preferencesDataStore(name = "bluetooth_state")

@InstallIn(SingletonComponent::class)
@Module
object BluetoothPersistenceModule {

    @Provides
    @Singleton
    fun provideNudgeAvailability(
        @ApplicationContext context: Context,
        @SerializationCapod json: Json,
    ): DataStoreValue<NudgeAvailability> = context.bluetoothDataStore.createValue(
        key = "core.bluetooth.nudge.availability",
        defaultValue = NudgeAvailability.UNKNOWN,
        json = json,
        onErrorFallbackToDefault = true,
    )
}
