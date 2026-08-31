package eu.darken.capod.monitor.core

import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.fromHex
import eu.darken.capod.common.toHex
import eu.darken.capod.pods.core.apple.ble.protocol.RPAChecker
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.logging.JUnitLogger

class RPACheckerTest : BaseTest() {

    private val irkHex = "79-04-65-1E-E2-CC-D9-26-F2-6E-20-EE-3E-CC-DE-79"
    private val resolvingAddress = "5A:16:2B:91:D1:CD"

    @Test
    fun `test check`() {
        val checker = RPAChecker()
        checker.verify(
            address = "5A:16:2B:91:D1:CD",
            irk = "79-04-65-1E-E2-CC-D9-26-F2-6E-20-EE-3E-CC-DE-79".fromHex(),
        ) shouldBe true
        checker.verify(
            address = "5A:16:2B:91:D1:CD",
            irk = "79-04-65-1E-E2-CC-D9-26-F2-6E-20-EE-3E-CC-DE-AA".fromHex(),
        ) shouldBe false
    }

    @Test
    fun `bad input check`() {
        val checker = RPAChecker()
        checker.verify(
            address = "5A:16:2B:91:D1:CD",
            irk = "".fromHex(),
        ) shouldBe false
        checker.verify(
            address = "",
            irk = "79-04-65-1E-E2-CC-D9-26-F2-6E-20-EE-3E-CC-DE-AA".fromHex(),
        ) shouldBe false
        checker.verify(
            address = "",
            irk = "".fromHex(),
        ) shouldBe false
    }

    @Test
    fun `resolve reports the standard octet order`() {
        val checker = RPAChecker()
        checker.resolve(
            address = resolvingAddress,
            irk = irkHex.fromHex(),
        ) shouldBe RPAChecker.AddressOrder.STANDARD
        checker.verify(
            address = resolvingAddress,
            irk = irkHex.fromHex(),
        ) shouldBe true
    }

    @Test
    fun `resolve reports the reversed octet order`() {
        val checker = RPAChecker()
        // The resolving address with its octets reversed, as a vendor stack delivering them backwards.
        checker.resolve(
            address = "CD:D1:91:2B:16:5A",
            irk = irkHex.fromHex(),
        ) shouldBe RPAChecker.AddressOrder.REVERSED
        checker.verify(
            address = "CD:D1:91:2B:16:5A",
            irk = irkHex.fromHex(),
        ) shouldBe true
    }

    @Test
    fun `a reversed candidate still has to pass the hash comparison`() {
        val checker = RPAChecker()
        // Reversed form 5B:16:2B:91:D1:CD is RPA-shaped, so the marker gate passes and only the
        // hash comparison can reject it.
        checker.resolve(
            address = "CD:D1:91:2B:16:5B",
            irk = irkHex.fromHex(),
        ).shouldBeNull()
        checker.verify(
            address = "CD:D1:91:2B:16:5B",
            irk = irkHex.fromHex(),
        ) shouldBe false
    }

    @Test
    fun `a reversed candidate is gated on the RPA type marker`() {
        val checker = RPAChecker()
        // Reversed form 1A:16:2B:85:33:DC resolves cryptographically, but its type marker is 00,
        // so only the gate rejects it.
        checker.resolve(
            address = "DC:33:85:2B:16:1A",
            irk = irkHex.fromHex(),
        ).shouldBeNull()
        checker.verify(
            address = "DC:33:85:2B:16:1A",
            irk = irkHex.fromHex(),
        ) shouldBe false
    }

    @Test
    fun `malformed addresses are rejected instead of mis-sliced`() {
        val checker = RPAChecker()
        // Seven components whose tail is the resolving address — sliced rather than rejected, this
        // resolves.
        checker.resolve(
            address = "00:$resolvingAddress",
            irk = irkHex.fromHex(),
        ).shouldBeNull()
        checker.verify(
            address = "00:$resolvingAddress",
            irk = irkHex.fromHex(),
        ) shouldBe false
        // 0x15A truncates to the resolving octet 0x5A.
        checker.resolve(
            address = "15A:16:2B:91:D1:CD",
            irk = irkHex.fromHex(),
        ).shouldBeNull()
        checker.verify(
            address = "15A:16:2B:91:D1:CD",
            irk = irkHex.fromHex(),
        ) shouldBe false
        checker.resolve(
            address = "5A:16:2B:91:D1",
            irk = irkHex.fromHex(),
        ).shouldBeNull()
    }

    @Test
    fun `the failure log carries neither the identity key nor the full address`() {
        val malformedIrk = "79-04-65-1E-E2-CC-D9-26-F2-6E-20-EE-3E-CC-DE".fromHex()
        val captured = mutableListOf<Pair<Logging.Priority, String>>()
        val capturingLogger = object : Logging.Logger {
            override fun log(
                priority: Logging.Priority,
                tag: String,
                message: String,
                metaData: Map<String, Any>?,
            ) {
                captured.add(priority to message)
            }
        }

        Logging.clearAll()
        Logging.install(capturingLogger)
        try {
            RPAChecker().verify(address = resolvingAddress, irk = malformedIrk) shouldBe false
        } finally {
            Logging.clearAll()
            Logging.install(JUnitLogger())
        }

        captured.any { (priority, message) ->
            priority == Logging.Priority.ERROR && message.contains("Failed to resolve RPA")
        } shouldBe true

        val keyHex = malformedIrk.toHex(separator = "")
        captured.any { (_, message) -> message.contains(keyHex, ignoreCase = true) } shouldBe false
        captured.any { (_, message) -> message.contains(resolvingAddress) } shouldBe false
    }

    @Test
    fun `a malformed address is logged as a warning`() {
        val captured = mutableListOf<Pair<Logging.Priority, String>>()
        val capturingLogger = object : Logging.Logger {
            override fun log(
                priority: Logging.Priority,
                tag: String,
                message: String,
                metaData: Map<String, Any>?,
            ) {
                captured.add(priority to message)
            }
        }

        Logging.clearAll()
        Logging.install(capturingLogger)
        try {
            RPAChecker().verify(address = "123", irk = irkHex.fromHex()) shouldBe false
        } finally {
            Logging.clearAll()
            Logging.install(JUnitLogger())
        }

        captured.any { (priority, message) ->
            priority == Logging.Priority.WARN && message.contains("malformed")
        } shouldBe true
    }
}