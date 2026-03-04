package eu.darken.capod.common.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.serialization.ByteArrayBase64Serializer
import eu.darken.capod.common.serialization.InstantEpochMillisSerializer
import eu.darken.capod.common.theming.ThemeColor
import eu.darken.capod.common.theming.ThemeMode
import eu.darken.capod.common.theming.ThemeStyle
import eu.darken.capod.common.upgrade.core.FossUpgrade
import eu.darken.capod.main.core.MonitorMode
import kotlinx.serialization.builtins.nullable
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesContainer
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.File
import java.time.Instant
import eu.darken.capod.common.datastore.value

class DataStoreValueSerializationTest : BaseTest() {

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
    fun `enum round-trip - ThemeMode`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("theme", ThemeMode.SYSTEM, json)

        pref.value() shouldBe ThemeMode.SYSTEM

        pref.value(ThemeMode.DARK)
        pref.value() shouldBe ThemeMode.DARK

        pref.value(ThemeMode.LIGHT)
        pref.value() shouldBe ThemeMode.LIGHT
    }

    @Test
    fun `enum round-trip - PodDevice Model`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("model", PodDevice.Model.UNKNOWN, json)

        PodDevice.Model.entries.forEach { model ->
            pref.value(model)
            pref.value() shouldBe model
        }
    }

    @Test
    fun `data class round-trip - DeviceProfilesContainer`() = runTest2 {
        val ds = createDataStore()
        val container = DeviceProfilesContainer(
            profiles = listOf(
                AppleDeviceProfile(
                    id = "test-id-1",
                    label = "My AirPods",
                    model = PodDevice.Model.AIRPODS_PRO,
                    address = "AA:BB:CC:DD:EE:FF",
                )
            )
        )
        val pref = ds.createValue("profiles", DeviceProfilesContainer(), json)

        pref.value(container)
        val result = pref.value()

        result.profiles.size shouldBe 1
        val profile = result.profiles[0] as AppleDeviceProfile
        profile.id shouldBe "test-id-1"
        profile.label shouldBe "My AirPods"
        profile.model shouldBe PodDevice.Model.AIRPODS_PRO
        profile.address shouldBe "AA:BB:CC:DD:EE:FF"
    }

    @Test
    fun `onErrorFallbackToDefault returns default on corrupt JSON`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("theme", ThemeMode.SYSTEM, json, onErrorFallbackToDefault = true)

        // Write corrupt JSON directly
        val corruptPref = ds.createValue("theme", "not valid json")
        corruptPref.value("{{{corrupt json")

        // Now read it as ThemeMode - should fallback to default
        pref.value() shouldBe ThemeMode.SYSTEM
    }

    @Test
    fun `onErrorFallbackToDefault returns default on unknown enum value`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("theme", ThemeMode.SYSTEM, json, onErrorFallbackToDefault = true)

        // Write an unknown enum value
        val rawPref = ds.createValue("theme", "placeholder")
        rawPref.value("\"theme.mode.nonexistent\"")

        pref.value() shouldBe ThemeMode.SYSTEM
    }

    @Test
    fun `onErrorFallbackToDefault false - corrupt JSON throws`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("theme", ThemeMode.SYSTEM, json, onErrorFallbackToDefault = false)

        val rawPref = ds.createValue("theme", "placeholder")
        rawPref.value("{{{corrupt")

        shouldThrow<Exception> {
            pref.value()
        }
    }

    @Test
    fun `ByteArray round-trip via explicit serializer`() = runTest2 {
        val ds = createDataStore()
        val testBytes = byteArrayOf(0x01, 0x02, 0x03, 0xAA.toByte(), 0xFF.toByte())

        val pref = ds.createValue(
            key = "bytes",
            defaultValue = null as ByteArray?,
            json = json,
            serializer = ByteArrayBase64Serializer.nullable,
        )

        pref.value() shouldBe null

        pref.value(testBytes)
        val result = pref.value()
        result!!.toList() shouldBe testBytes.toList()
    }

    @Test
    fun `enum round-trip - ThemeStyle`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("style", ThemeStyle.DEFAULT, json)

        ThemeStyle.entries.forEach { style ->
            pref.value(style)
            pref.value() shouldBe style
        }
    }

    @Test
    fun `enum round-trip - ThemeColor`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("color", ThemeColor.BLUE, json)

        ThemeColor.entries.forEach { color ->
            pref.value(color)
            pref.value() shouldBe color
        }
    }

    @Test
    fun `enum round-trip - MonitorMode`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("monitor", MonitorMode.MANUAL, json)

        MonitorMode.entries.forEach { mode ->
            pref.value(mode)
            pref.value() shouldBe mode
        }
    }

    @Test
    fun `enum round-trip - ScannerMode`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("scanner", ScannerMode.LOW_POWER, json)

        ScannerMode.entries.forEach { mode ->
            pref.value(mode)
            pref.value() shouldBe mode
        }
    }

    @Test
    fun `enum round-trip - AutoConnectCondition`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("autoconnect", AutoConnectCondition.WHEN_SEEN, json)

        AutoConnectCondition.entries.forEach { condition ->
            pref.value(condition)
            pref.value() shouldBe condition
        }
    }

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
    fun `InstantEpochMillisSerializer round-trip`() {
        val instant = Instant.ofEpochMilli(1709553600000)
        val serialized = json.encodeToString(InstantEpochMillisSerializer, instant)
        serialized shouldBe "1709553600000"
        val deserialized = json.decodeFromString(InstantEpochMillisSerializer, serialized)
        deserialized shouldBe instant
    }

    @Test
    fun `InstantEpochMillisSerializer - epoch zero`() {
        val instant = Instant.EPOCH
        val serialized = json.encodeToString(InstantEpochMillisSerializer, instant)
        serialized shouldBe "0"
        val deserialized = json.decodeFromString(InstantEpochMillisSerializer, serialized)
        deserialized shouldBe instant
    }

    @Test
    fun `sealed interface polymorphic - DeviceProfile`() = runTest2 {
        val ds = createDataStore()
        val profile: DeviceProfile = AppleDeviceProfile(
            id = "poly-test",
            label = "Test Profile",
            model = PodDevice.Model.AIRPODS_GEN2,
            identityKey = byteArrayOf(0x01, 0x02),
            encryptionKey = byteArrayOf(0x03, 0x04),
        )
        val container = DeviceProfilesContainer(profiles = listOf(profile))
        val pref = ds.createValue("profiles", DeviceProfilesContainer(), json)

        pref.value(container)
        val result = pref.value()

        result.profiles.size shouldBe 1
        val restored = result.profiles[0] as AppleDeviceProfile
        restored.id shouldBe "poly-test"
        restored.label shouldBe "Test Profile"
        restored.model shouldBe PodDevice.Model.AIRPODS_GEN2
        restored.identityKey!!.toList() shouldBe listOf<Byte>(0x01, 0x02)
        restored.encryptionKey!!.toList() shouldBe listOf<Byte>(0x03, 0x04)
    }
}
