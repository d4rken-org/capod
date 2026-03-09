package eu.darken.capod.common.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import eu.darken.capod.common.upgrade.core.FossUpgrade
import io.kotest.matchers.shouldBe
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
        classDiscriminator = "type"
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
    fun `FossUpgrade Reason encodes to canonical string`() {
        json.encodeToString(serializer<FossUpgrade.Reason>(), FossUpgrade.Reason.DONATED) shouldBe "\"foss.upgrade.reason.donated\""
    }

    @Test
    fun `legacy FossUpgrade JSON decodes correctly`() {
        val legacyJson = """{"upgradedAt":1709553600000,"reason":"foss.upgrade.reason.donated"}"""
        val result = json.decodeFromString(serializer<FossUpgrade>(), legacyJson)
        result.upgradedAt shouldBe Instant.ofEpochMilli(1709553600000)
        result.reason shouldBe FossUpgrade.Reason.DONATED
    }

    @Test
    fun `FossUpgrade round-trip`() {
        val upgrade = FossUpgrade(
            upgradedAt = Instant.ofEpochMilli(1709553600000),
            reason = FossUpgrade.Reason.ALREADY_DONATED,
        )
        val encoded = json.encodeToString(serializer<FossUpgrade>(), upgrade)
        val decoded = json.decodeFromString(serializer<FossUpgrade>(), encoded)
        decoded.upgradedAt shouldBe upgrade.upgradedAt
        decoded.reason shouldBe upgrade.reason
    }
}
