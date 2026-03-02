package eu.darken.capod.common.datastore

import com.squareup.moshi.Json as MoshiJson
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * Tests that verify @SerialName values match @Json(name=...) values,
 * ensuring Moshi-serialized SharedPreferences data can be read by kotlinx-serialization
 * after the DataStore migration.
 */
class DataStoreMigrationCompatTest : BaseTest() {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * For each enum with both @SerialName and @Json annotations,
     * verify they produce the same string. This catches typos in @SerialName values.
     */
    private inline fun <reified T : Enum<T>> verifyEnumSerialNameParity() {
        val enumClass = T::class.java
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
    fun `SerialName matches Json name - ThemeMode`() = verifyEnumSerialNameParity<ThemeMode>()

    @Test
    fun `SerialName matches Json name - ThemeStyle`() = verifyEnumSerialNameParity<ThemeStyle>()

    @Test
    fun `SerialName matches Json name - ThemeColor`() = verifyEnumSerialNameParity<ThemeColor>()

    @Test
    fun `SerialName matches Json name - MonitorMode`() = verifyEnumSerialNameParity<MonitorMode>()

    @Test
    fun `SerialName matches Json name - ScannerMode`() = verifyEnumSerialNameParity<ScannerMode>()

    @Test
    fun `SerialName matches Json name - AutoConnectCondition`() = verifyEnumSerialNameParity<AutoConnectCondition>()

    @Test
    fun `SerialName matches Json name - PodDevice Model`() = verifyEnumSerialNameParity<PodDevice.Model>()

    @Test
    fun `Moshi-serialized ThemeMode string is readable by kotlinx`() {
        // Moshi stores enums as JSON strings like: "theme.mode.dark"
        val moshiOutput = "\"theme.mode.dark\""
        val result = json.decodeFromString(serializer<ThemeMode>(), moshiOutput)
        result shouldBe ThemeMode.DARK
    }

    @Test
    fun `Moshi-serialized ScannerMode string is readable by kotlinx`() {
        val moshiOutput = "\"scanner.mode.balanced\""
        val result = json.decodeFromString(serializer<ScannerMode>(), moshiOutput)
        result shouldBe ScannerMode.BALANCED
    }

    @Test
    fun `Moshi-serialized MonitorMode string is readable by kotlinx`() {
        val moshiOutput = "\"monitor.mode.automatic\""
        val result = json.decodeFromString(serializer<MonitorMode>(), moshiOutput)
        result shouldBe MonitorMode.AUTOMATIC
    }

    @Test
    fun `all PodDevice Model values can be decoded from Moshi format`() {
        for (model in PodDevice.Model.entries) {
            val field = PodDevice.Model::class.java.getField(model.name)
            val moshiAnnotation = field.getAnnotation(MoshiJson::class.java)
            if (moshiAnnotation != null) {
                val moshiOutput = "\"${moshiAnnotation.name}\""
                val result = json.decodeFromString(serializer<PodDevice.Model>(), moshiOutput)
                result shouldBe model
            }
        }
    }

    @Test
    fun `Moshi-serialized DeviceProfilesContainer JSON is readable by kotlinx`() {
        // This is what Moshi would produce for a container with one Apple profile
        val moshiJson = """
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

        val result = json.decodeFromString(serializer<DeviceProfilesContainer>(), moshiJson)
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
    fun `Moshi-serialized DeviceProfile with ByteArray fields is readable by kotlinx`() {
        // Base64 encoded: [0x01, 0x02, 0x03] = "AQID"
        val moshiJson = """
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

        val result = json.decodeFromString(serializer<DeviceProfilesContainer>(), moshiJson)
        val profile = result.profiles[0] as AppleDeviceProfile
        profile.identityKey!!.toList() shouldBe listOf<Byte>(0x01, 0x02, 0x03)
        profile.encryptionKey!!.toList() shouldBe listOf<Byte>(0x04, 0x05, 0x06)
    }
}
