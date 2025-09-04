package eu.darken.capod.common.serialization

import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class SerializationModule {

    @Provides
    @Singleton
    fun moshi(): Moshi = Moshi.Builder()
        .add(JavaInstantAdapter())
        .add(ByteArrayAdapter())
        .build()

}
