package eu.darken.capod.common.serialization

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.capod.profiles.core.DeviceProfile
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class SerializationModule {

    @Provides
    @Singleton
    fun moshi(): Moshi = Moshi.Builder().apply {
        add(JavaInstantAdapter())
        add(ByteArrayAdapter())
        add(DeviceProfile.MOSHI_FACTORY)
    }.build()

}
