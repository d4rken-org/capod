package eu.darken.capod.common.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.squareup.moshi.Json as MoshiJson
import eu.darken.capod.common.upgrade.core.FossUpgrade
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.File
import java.time.Instant

class FossUpgradeSerializationTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private var dsCounter = 0

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun createDataStore() = PreferenceDataStoreFactory.create(
        produceFile = { File(tempDir, "test_${dsCounter++}.preferences_pb") }
    )

    @Test
    fun `data class round-trip - FossUpgrade nullable`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue<FossUpgrade?>("upgrade", null, json)

        pref.value() shouldBe null

        val upgrade = FossUpgrade(
            upgradedAt = Instant.ofEpochMilli(1709553600000),
            reason = FossUpgrade.Reason.DONATED,
        )
        pref.value(upgrade)
        val result = pref.value()

        result!!.upgradedAt shouldBe Instant.ofEpochMilli(1709553600000)
        result.reason shouldBe FossUpgrade.Reason.DONATED
    }

    @Test
    fun `SerialName matches Json name - FossUpgrade Reason`() {
        val enumClass = FossUpgrade.Reason::class.java
        for (constant in enumClass.enumConstants!!) {
            val field = enumClass.getField(constant.name)
            val moshiAnnotation = field.getAnnotation(MoshiJson::class.java)
            val serialNameAnnotation = field.getAnnotation(SerialName::class.java)

            if (moshiAnnotation != null && serialNameAnnotation != null) {
                serialNameAnnotation.value shouldBe moshiAnnotation.name
            }
        }
    }

    @Test
    fun `Moshi-serialized FossUpgrade JSON is readable by kotlinx`() {
        val moshiJson = """{"upgradedAt":1709553600000,"reason":"foss.upgrade.reason.donated"}"""
        val result = json.decodeFromString(serializer<FossUpgrade>(), moshiJson)
        result.upgradedAt shouldBe Instant.ofEpochMilli(1709553600000)
        result.reason shouldBe FossUpgrade.Reason.DONATED
    }
}
