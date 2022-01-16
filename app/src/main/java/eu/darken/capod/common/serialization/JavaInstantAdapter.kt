package eu.darken.capod.common.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Instant

class JavaInstantAdapter {
    @ToJson
    fun toJson(obj: Instant): Long = obj.toEpochMilli()

    @FromJson
    fun fromJson(epochMillis: Long): Instant = Instant.ofEpochSecond(epochMillis)
}