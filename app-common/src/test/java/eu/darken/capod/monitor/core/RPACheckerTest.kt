package eu.darken.capod.monitor.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class RPACheckerTest : BaseTest() {

    @Test
    fun `test check`() {
        val checker = RPAChecker()
        checker.verify(
            address = "5A:16:2B:91:D1:CD",
            irk = "79-04-65-1E-E2-CC-D9-26-F2-6E-20-EE-3E-CC-DE-79"
                .replace("-", "")
                .also { require(it.length % 2 == 0) { "Not a HEX string" } }
                .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        ) shouldBe true
    }
}