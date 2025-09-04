package eu.darken.capod.monitor.core

import eu.darken.capod.common.fromHex
import eu.darken.capod.pods.core.apple.protocol.RPAChecker
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class RPACheckerTest : BaseTest() {

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
}