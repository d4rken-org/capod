package eu.darken.capod.common.serialization

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Suppress("unused")
@OptIn(ExperimentalEncodingApi::class)
class ByteArrayAdapter {
    @ToJson
    fun toJson(obj: ByteArray): String = Base64.encode(obj)

    @FromJson
    fun fromJson(base64: String): ByteArray? = Base64.decode(base64)
}