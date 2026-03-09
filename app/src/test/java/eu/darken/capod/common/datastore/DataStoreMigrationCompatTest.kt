package eu.darken.capod.common.datastore

import eu.darken.capod.common.bluetooth.ScannerMode
import eu.darken.capod.common.theming.ThemeColor
import eu.darken.capod.common.theming.ThemeMode
import eu.darken.capod.common.theming.ThemeStyle
import eu.darken.capod.main.core.MonitorMode
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.profiles.core.AppleDeviceProfile
import eu.darken.capod.profiles.core.DeviceProfilesContainer
import eu.darken.capod.reaction.core.autoconnect.AutoConnectCondition
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Tests that verify kotlinx-serialization produces the expected wire format
 * and can decode legacy JSON strings (originally written by Moshi).
 */
class DataStoreMigrationCompatTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    @Test
    fun `ThemeMode encodes to canonical string`() {
        json.encodeToString(serializer<ThemeMode>(), ThemeMode.DARK) shouldBe "\"theme.mode.dark\""
    }

    @Test
    fun `ThemeMode legacy string decodes correctly`() {
        val legacyOutput = "\"theme.mode.dark\""
        json.decodeFromString(serializer<ThemeMode>(), legacyOutput) shouldBe ThemeMode.DARK
    }

    @Test
    fun `ThemeMode round-trip`() {
        ThemeMode.entries.forEach { mode ->
            val encoded = json.encodeToString(serializer<ThemeMode>(), mode)
            json.decodeFromString(serializer<ThemeMode>(), encoded) shouldBe mode
        }
    }

    @Test
    fun `ScannerMode encodes to canonical string`() {
        json.encodeToString(serializer<ScannerMode>(), ScannerMode.BALANCED) shouldBe "\"scanner.mode.balanced\""
    }

    @Test
    fun `ScannerMode legacy string decodes correctly`() {
        val legacyOutput = "\"scanner.mode.balanced\""
        json.decodeFromString(serializer<ScannerMode>(), legacyOutput) shouldBe ScannerMode.BALANCED
    }

    @Test
    fun `ScannerMode round-trip`() {
        ScannerMode.entries.forEach { mode ->
            val encoded = json.encodeToString(serializer<ScannerMode>(), mode)
            json.decodeFromString(serializer<ScannerMode>(), encoded) shouldBe mode
        }
    }

    @Test
    fun `MonitorMode encodes to canonical string`() {
        json.encodeToString(serializer<MonitorMode>(), MonitorMode.AUTOMATIC) shouldBe "\"monitor.mode.automatic\""
    }

    @Test
    fun `MonitorMode legacy string decodes correctly`() {
        val legacyOutput = "\"monitor.mode.automatic\""
        json.decodeFromString(serializer<MonitorMode>(), legacyOutput) shouldBe MonitorMode.AUTOMATIC
    }

    @Test
    fun `MonitorMode round-trip`() {
        MonitorMode.entries.forEach { mode ->
            val encoded = json.encodeToString(serializer<MonitorMode>(), mode)
            json.decodeFromString(serializer<MonitorMode>(), encoded) shouldBe mode
        }
    }

    @Test
    fun `ThemeStyle encodes to canonical string`() {
        json.encodeToString(serializer<ThemeStyle>(), ThemeStyle.MATERIAL_YOU) shouldBe "\"theme.style.materialyou\""
    }

    @Test
    fun `ThemeStyle legacy string decodes correctly`() {
        val legacyOutput = "\"theme.style.materialyou\""
        json.decodeFromString(serializer<ThemeStyle>(), legacyOutput) shouldBe ThemeStyle.MATERIAL_YOU
    }

    @Test
    fun `ThemeStyle round-trip`() {
        ThemeStyle.entries.forEach { style ->
            val encoded = json.encodeToString(serializer<ThemeStyle>(), style)
            json.decodeFromString(serializer<ThemeStyle>(), encoded) shouldBe style
        }
    }

    @Test
    fun `ThemeColor encodes to canonical string`() {
        json.encodeToString(serializer<ThemeColor>(), ThemeColor.GREEN) shouldBe "\"theme.color.green\""
    }

    @Test
    fun `ThemeColor legacy string decodes correctly`() {
        val legacyOutput = "\"theme.color.green\""
        json.decodeFromString(serializer<ThemeColor>(), legacyOutput) shouldBe ThemeColor.GREEN
    }

    @Test
    fun `ThemeColor round-trip`() {
        ThemeColor.entries.forEach { color ->
            val encoded = json.encodeToString(serializer<ThemeColor>(), color)
            json.decodeFromString(serializer<ThemeColor>(), encoded) shouldBe color
        }
    }

    @Test
    fun `AutoConnectCondition encodes to canonical string`() {
        json.encodeToString(serializer<AutoConnectCondition>(), AutoConnectCondition.WHEN_SEEN) shouldBe "\"autoconnect.condition.seen\""
    }

    @Test
    fun `AutoConnectCondition legacy string decodes correctly`() {
        val legacyOutput = "\"autoconnect.condition.seen\""
        json.decodeFromString(serializer<AutoConnectCondition>(), legacyOutput) shouldBe AutoConnectCondition.WHEN_SEEN
    }

    @Test
    fun `AutoConnectCondition round-trip`() {
        AutoConnectCondition.entries.forEach { condition ->
            val encoded = json.encodeToString(serializer<AutoConnectCondition>(), condition)
            json.decodeFromString(serializer<AutoConnectCondition>(), encoded) shouldBe condition
        }
    }

    @Test
    fun `PodDevice Model encodes to canonical string`() {
        json.encodeToString(serializer<PodDevice.Model>(), PodDevice.Model.AIRPODS_PRO) shouldBe "\"airpods.pro\""
    }

    @Test
    fun `all PodDevice Model values round-trip`() {
        for (model in PodDevice.Model.entries) {
            val encoded = json.encodeToString(serializer<PodDevice.Model>(), model)
            json.decodeFromString(serializer<PodDevice.Model>(), encoded) shouldBe model
        }
    }

    @Test
    fun `PodDevice Model legacy string decodes correctly`() {
        val legacyOutput = "\"airpods.pro\""
        json.decodeFromString(serializer<PodDevice.Model>(), legacyOutput) shouldBe PodDevice.Model.AIRPODS_PRO
    }

    @Test
    fun `legacy DeviceProfilesContainer JSON decodes correctly`() {
        val legacyJson = """
            {
                "profiles": [
                    {
                        "type": "apple",
                        "id": "test-uuid",
                        "label": "My AirPods Pro",
                        "priority": 0,
                        "model": "airpods.pro",
                        "minimumSignalQuality": 0.15,
                        "address": "AA:BB:CC:DD:EE:FF"
                    }
                ]
            }
        """.trimIndent()

        val result = json.decodeFromString(serializer<DeviceProfilesContainer>(), legacyJson)
        result.profiles.size shouldBe 1

        val profile = result.profiles[0] as AppleDeviceProfile
        profile.id shouldBe "test-uuid"
        profile.label shouldBe "My AirPods Pro"
        profile.model shouldBe PodDevice.Model.AIRPODS_PRO
        profile.minimumSignalQuality shouldBe 0.15f
        profile.address shouldBe "AA:BB:CC:DD:EE:FF"
        profile.identityKey shouldBe null
        profile.encryptionKey shouldBe null
    }

    @Test
    fun `DeviceProfilesContainer round-trip`() {
        val container = DeviceProfilesContainer(
            profiles = listOf(
                AppleDeviceProfile(
                    id = "round-trip-id",
                    label = "Test Profile",
                    model = PodDevice.Model.AIRPODS_GEN2,
                    address = "11:22:33:44:55:66",
                )
            )
        )
        val encoded = json.encodeToString(serializer<DeviceProfilesContainer>(), container)
        val decoded = json.decodeFromString(serializer<DeviceProfilesContainer>(), encoded)

        decoded.profiles.size shouldBe 1
        val profile = decoded.profiles[0] as AppleDeviceProfile
        profile.id shouldBe "round-trip-id"
        profile.label shouldBe "Test Profile"
        profile.model shouldBe PodDevice.Model.AIRPODS_GEN2
        profile.address shouldBe "11:22:33:44:55:66"
    }

    @Test
    fun `DeviceProfilesContainer includes type discriminator`() {
        val container = DeviceProfilesContainer(
            profiles = listOf(
                AppleDeviceProfile(id = "disc-test", label = "Test")
            )
        )
        val encoded = json.encodeToString(serializer<DeviceProfilesContainer>(), container)
        encoded shouldContain "\"type\":\"apple\""
    }

    @Test
    fun `legacy DeviceProfile with ByteArray fields decodes correctly`() {
        // Base64 encoded: [0x01, 0x02, 0x03] = "AQID"
        val legacyJson = """
            {
                "profiles": [
                    {
                        "type": "apple",
                        "id": "key-test",
                        "label": "Keyed Profile",
                        "priority": 1,
                        "model": "airpods.gen2",
                        "minimumSignalQuality": 0.2,
                        "identityKey": "AQID",
                        "encryptionKey": "BAUG"
                    }
                ]
            }
        """.trimIndent()

        val result = json.decodeFromString(serializer<DeviceProfilesContainer>(), legacyJson)
        val profile = result.profiles[0] as AppleDeviceProfile
        profile.identityKey!!.toList() shouldBe listOf<Byte>(0x01, 0x02, 0x03)
        profile.encryptionKey!!.toList() shouldBe listOf<Byte>(0x04, 0x05, 0x06)
    }
}
