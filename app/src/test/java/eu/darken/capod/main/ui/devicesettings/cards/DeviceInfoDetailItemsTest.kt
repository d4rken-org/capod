package eu.darken.capod.main.ui.devicesettings.cards

import eu.darken.capod.pods.core.apple.aap.protocol.AapDeviceInfo
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class DeviceInfoDetailItemsTest : BaseTest() {

    private val labels = DeviceInfoDetailLabels(
        manufacturer = "Manufacturer",
        hardware = "Hardware",
        serial = "Serial Number",
        firmware = "Firmware",
        firmwarePending = "Pending Firmware",
        build = "Build",
        leftSerial = "Left Pod Serial",
        rightSerial = "Right Pod Serial",
        leftBonded = "Left Bonded",
        rightBonded = "Right Bonded",
    )

    private val formatter: (Instant) -> String = { "fmt:${it.epochSecond}" }

    private fun info(
        manufacturer: String = "",
        serialNumber: String = "",
        firmwareVersion: String = "",
        firmwareVersionPending: String? = null,
        hardwareVersion: String? = null,
        leftEarbudSerial: String? = null,
        rightEarbudSerial: String? = null,
        marketingVersion: String? = null,
        leftEarbudFirstPaired: Instant? = null,
        rightEarbudFirstPaired: Instant? = null,
    ) = AapDeviceInfo(
        name = "AirPods",
        modelNumber = "A2084",
        manufacturer = manufacturer,
        serialNumber = serialNumber,
        firmwareVersion = firmwareVersion,
        firmwareVersionPending = firmwareVersionPending,
        hardwareVersion = hardwareVersion,
        leftEarbudSerial = leftEarbudSerial,
        rightEarbudSerial = rightEarbudSerial,
        marketingVersion = marketingVersion,
        leftEarbudFirstPaired = leftEarbudFirstPaired,
        rightEarbudFirstPaired = rightEarbudFirstPaired,
    )

    @Test
    fun `null AapDeviceInfo yields empty list`() {
        buildDeviceInfoDetailItems(null, labels, formatter) shouldBe emptyList()
    }

    @Test
    fun `minimal info renders only populated rows`() {
        val result = buildDeviceInfoDetailItems(
            info(manufacturer = "Apple", serialNumber = "ABC123", firmwareVersion = "7A305"),
            labels,
            formatter,
        )
        result shouldContainExactly listOf(
            DeviceDetailItem.Single("Manufacturer", "Apple"),
            DeviceDetailItem.Single("Serial Number", "ABC123"),
            DeviceDetailItem.Single("Firmware", "7A305"),
        )
    }

    @Test
    fun `hardware row sits between manufacturer and serial`() {
        val result = buildDeviceInfoDetailItems(
            info(
                manufacturer = "Apple",
                hardwareVersion = "1.0.0",
                serialNumber = "ABC123",
                firmwareVersion = "7A305",
            ),
            labels,
            formatter,
        )
        result shouldContainExactly listOf(
            DeviceDetailItem.Single("Manufacturer", "Apple"),
            DeviceDetailItem.Single("Hardware", "1.0.0"),
            DeviceDetailItem.Single("Serial Number", "ABC123"),
            DeviceDetailItem.Single("Firmware", "7A305"),
        )
    }

    @Test
    fun `pending firmware row sits between firmware and build`() {
        val result = buildDeviceInfoDetailItems(
            info(
                manufacturer = "Apple",
                serialNumber = "ABC123",
                firmwareVersion = "81.26",
                firmwareVersionPending = "82.10",
                marketingVersion = "8454768",
            ),
            labels,
            formatter,
        )
        result shouldContainExactly listOf(
            DeviceDetailItem.Single("Manufacturer", "Apple"),
            DeviceDetailItem.Single("Serial Number", "ABC123"),
            DeviceDetailItem.Single("Firmware", "81.26"),
            DeviceDetailItem.Single("Pending Firmware", "82.10"),
            DeviceDetailItem.Single("Build", "8454768"),
        )
    }

    @Test
    fun `null pending firmware omits the row`() {
        val result = buildDeviceInfoDetailItems(
            info(firmwareVersion = "81.26", marketingVersion = "8454768"),
            labels,
            formatter,
        )
        result shouldContainExactly listOf(
            DeviceDetailItem.Single("Firmware", "81.26"),
            DeviceDetailItem.Single("Build", "8454768"),
        )
    }

    @Test
    fun `blank pending firmware omits the row`() {
        val result = buildDeviceInfoDetailItems(
            info(firmwareVersion = "81.26", firmwareVersionPending = "   "),
            labels,
            formatter,
        )
        result shouldContainExactly listOf(
            DeviceDetailItem.Single("Firmware", "81.26"),
        )
    }

    @Test
    fun `both serials present yield a Paired row`() {
        val result = buildDeviceInfoDetailItems(
            info(leftEarbudSerial = "LLL", rightEarbudSerial = "RRR"),
            labels,
            formatter,
        )
        result shouldContainExactly listOf(
            DeviceDetailItem.Paired(
                start = DeviceDetailItem.Single("Left Pod Serial", "LLL"),
                end = DeviceDetailItem.Single("Right Pod Serial", "RRR"),
            ),
        )
    }

    @Test
    fun `only left serial yields a Single row`() {
        val result = buildDeviceInfoDetailItems(
            info(leftEarbudSerial = "LLL"),
            labels,
            formatter,
        )
        result shouldContainExactly listOf(
            DeviceDetailItem.Single("Left Pod Serial", "LLL"),
        )
    }

    @Test
    fun `only right serial yields a Single row`() {
        val result = buildDeviceInfoDetailItems(
            info(rightEarbudSerial = "RRR"),
            labels,
            formatter,
        )
        result shouldContainExactly listOf(
            DeviceDetailItem.Single("Right Pod Serial", "RRR"),
        )
    }

    @Test
    fun `both bonded dates present always yield a Paired row`() {
        val sameSecond = Instant.ofEpochSecond(1697480211L)
        val result = buildDeviceInfoDetailItems(
            info(leftEarbudFirstPaired = sameSecond, rightEarbudFirstPaired = sameSecond),
            labels,
            formatter,
        )
        result shouldContainExactly listOf(
            DeviceDetailItem.Paired(
                start = DeviceDetailItem.Single("Left Bonded", "fmt:1697480211"),
                end = DeviceDetailItem.Single("Right Bonded", "fmt:1697480211"),
            ),
        )
    }

    @Test
    fun `both bonded dates with different formatted values yield a Paired row`() {
        val left = Instant.ofEpochSecond(1708000000L)
        val right = Instant.ofEpochSecond(1697480211L)
        val result = buildDeviceInfoDetailItems(
            info(leftEarbudFirstPaired = left, rightEarbudFirstPaired = right),
            labels,
            formatter,
        )
        result shouldContainExactly listOf(
            DeviceDetailItem.Paired(
                start = DeviceDetailItem.Single("Left Bonded", "fmt:1708000000"),
                end = DeviceDetailItem.Single("Right Bonded", "fmt:1697480211"),
            ),
        )
    }

    @Test
    fun `only left bonded date yields a Single row`() {
        val result = buildDeviceInfoDetailItems(
            info(leftEarbudFirstPaired = Instant.ofEpochSecond(1697480211L)),
            labels,
            formatter,
        )
        result shouldContainExactly listOf(
            DeviceDetailItem.Single("Left Bonded", "fmt:1697480211"),
        )
    }

    @Test
    fun `only right bonded date yields a Single row`() {
        val result = buildDeviceInfoDetailItems(
            info(rightEarbudFirstPaired = Instant.ofEpochSecond(1697480211L)),
            labels,
            formatter,
        )
        result shouldContainExactly listOf(
            DeviceDetailItem.Single("Right Bonded", "fmt:1697480211"),
        )
    }

    @Test
    fun `both bonded dates null yields no bonded row`() {
        val result = buildDeviceInfoDetailItems(
            info(
                manufacturer = "Apple",
                leftEarbudFirstPaired = null,
                rightEarbudFirstPaired = null,
            ),
            labels,
            formatter,
        )
        result.none { it is DeviceDetailItem.Paired } shouldBe true
        result.none {
            it is DeviceDetailItem.Single &&
                (it.label == labels.leftBonded || it.label == labels.rightBonded)
        } shouldBe true
    }
}
