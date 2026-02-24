package eu.darken.capod.common.preferences

import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import eu.darken.capod.common.theming.ThemeMode
import eu.darken.capod.main.core.MonitorMode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.assertThrows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson
import testhelpers.preferences.MockSharedPreferences

class FlowPreferenceMoshiTest : BaseTest() {

    private val mockPreferences = MockSharedPreferences()

    @JsonClass(generateAdapter = true)
    data class TestGson(
        val string: String = "",
        val boolean: Boolean = true,
        val float: Float = 1.0f,
        val int: Int = 1,
        val long: Long = 1L
    )

    @Test
    fun `reading and writing using manual reader and writer`() = runTest {
        val testData1 = TestGson(string = "teststring")
        val testData2 = TestGson(string = "update")
        val moshi = Moshi.Builder().build()
        FlowPreference<TestGson?>(
            preferences = mockPreferences,
            key = "testKey",
            rawReader = moshiReader(moshi, testData1),
            rawWriter = moshiWriter(moshi)
        ).apply {
            value shouldBe testData1
            flow.first() shouldBe testData1
            mockPreferences.dataMapPeek.values.isEmpty() shouldBe true

            update {
                it shouldBe testData1
                it!!.copy(string = "update")
            }

            value shouldBe testData2
            flow.first() shouldBe testData2
            (mockPreferences.dataMapPeek.values.first() as String).toComparableJson() shouldBe """
                {
                    "string":"update",
                    "boolean":true,
                    "float":1.0,
                    "int":1,
                    "long":1
                }
            """.toComparableJson()

            update {
                it shouldBe testData2
                null
            }
            value shouldBe testData1
            flow.first() shouldBe testData1
            mockPreferences.dataMapPeek.values.isEmpty() shouldBe true
        }
    }

    @Test
    fun `reading and writing using autocreated reader and writer`() = runTest {
        val testData1 = TestGson(string = "teststring")
        val testData2 = TestGson(string = "update")
        val moshi = Moshi.Builder().build()

        mockPreferences.createFlowPreference<TestGson?>(
            key = "testKey",
            defaultValue = testData1,
            moshi = moshi
        ).apply {
            value shouldBe testData1
            flow.first() shouldBe testData1
            mockPreferences.dataMapPeek.values.isEmpty() shouldBe true

            update {
                it shouldBe testData1
                it!!.copy(string = "update")
            }

            value shouldBe testData2
            flow.first() shouldBe testData2
            (mockPreferences.dataMapPeek.values.first() as String).toComparableJson() shouldBe """
                {
                    "string":"update",
                    "boolean":true,
                    "float":1.0,
                    "int":1,
                    "long":1
                }
            """.toComparableJson()

            update {
                it shouldBe testData2
                null
            }
            value shouldBe testData1
            flow.first() shouldBe testData1
            mockPreferences.dataMapPeek.values.isEmpty() shouldBe true
        }
    }

    @Test
    fun `enum serialization`() = runTest {
        val moshi = Moshi.Builder().build()
        val monitorMode = mockPreferences.createFlowPreference(
            "core.monitor.mode",
            MonitorMode.AUTOMATIC,
            moshi
        )

        monitorMode.value shouldBe MonitorMode.AUTOMATIC
        monitorMode.update { MonitorMode.MANUAL }
        monitorMode.value shouldBe MonitorMode.MANUAL
    }

    @Test
    fun `bad enum value throws without fallback`() = runTest {
        val moshi = Moshi.Builder().build()
        mockPreferences.edit().putString("theme.mode", "\"theme.mode.bogus\"").apply()

        assertThrows<JsonDataException> {
            mockPreferences.createFlowPreference(
                key = "theme.mode",
                defaultValue = ThemeMode.SYSTEM,
                moshi = moshi,
                onErrorFallbackToDefault = false,
            )
        }
    }

    @Test
    fun `bad enum value returns default with fallback`() = runTest {
        val moshi = Moshi.Builder().build()
        mockPreferences.edit().putString("theme.mode", "\"theme.mode.bogus\"").apply()

        val pref = mockPreferences.createFlowPreference(
            key = "theme.mode",
            defaultValue = ThemeMode.SYSTEM,
            moshi = moshi,
            onErrorFallbackToDefault = true,
        )

        pref.value shouldBe ThemeMode.SYSTEM
        pref.flow.first() shouldBe ThemeMode.SYSTEM
    }

    @Test
    fun `corrupt json returns default with fallback`() = runTest {
        val moshi = Moshi.Builder().build()
        mockPreferences.edit().putString("theme.mode", "not-json-at-all").apply()

        val pref = mockPreferences.createFlowPreference(
            key = "theme.mode",
            defaultValue = ThemeMode.DARK,
            moshi = moshi,
            onErrorFallbackToDefault = true,
        )

        pref.value shouldBe ThemeMode.DARK
    }

    @Test
    fun `valid enum roundtrips with fallback enabled`() = runTest {
        val moshi = Moshi.Builder().build()
        val pref = mockPreferences.createFlowPreference(
            key = "theme.mode",
            defaultValue = ThemeMode.SYSTEM,
            moshi = moshi,
            onErrorFallbackToDefault = true,
        )

        pref.value shouldBe ThemeMode.SYSTEM
        pref.update { ThemeMode.DARK }
        pref.value shouldBe ThemeMode.DARK
        pref.flow.first() shouldBe ThemeMode.DARK
    }
}
