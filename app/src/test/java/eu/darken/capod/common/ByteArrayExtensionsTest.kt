package eu.darken.capod.common

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ByteArrayExtensionsTest : BaseTest() {

    @Test
    fun `hex - ByteArray conversion`() = runTest {
        val addr = "78-73-AF-B4-85-22"
        val raw = addr.fromHex()
        raw.toHex() shouldBe addr
    }
}