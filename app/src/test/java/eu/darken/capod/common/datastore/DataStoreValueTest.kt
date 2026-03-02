package eu.darken.capod.common.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import java.io.File
import eu.darken.capod.common.datastore.valueBlocking
import eu.darken.capod.common.datastore.value

class DataStoreValueTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private var dsCounter = 0

    private fun createDataStore() = PreferenceDataStoreFactory.create(
        produceFile = { File(tempDir, "test_${dsCounter++}.preferences_pb") }
    )

    @Test
    fun `read default value when key not set - String`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", "default_val")
        pref.value() shouldBe "default_val"
    }

    @Test
    fun `read default value when key not set - Boolean`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", false)
        pref.value() shouldBe false
    }

    @Test
    fun `read default value when key not set - Int`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", 42)
        pref.value() shouldBe 42
    }

    @Test
    fun `read default value when key not set - Long`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", 123L)
        pref.value() shouldBe 123L
    }

    @Test
    fun `read default value when key not set - Float`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", 0.5f)
        pref.value() shouldBe 0.5f
    }

    @Test
    fun `write and read back String`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", "default")
        pref.value("new_value")
        pref.value() shouldBe "new_value"
    }

    @Test
    fun `write and read back Boolean`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", false)
        pref.value(true)
        pref.value() shouldBe true
    }

    @Test
    fun `write and read back Int`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", 0)
        pref.value(99)
        pref.value() shouldBe 99
    }

    @Test
    fun `write and read back Long`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", 0L)
        pref.value(Long.MAX_VALUE)
        pref.value() shouldBe Long.MAX_VALUE
    }

    @Test
    fun `write and read back Float`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", 0f)
        pref.value(3.14f)
        pref.value() shouldBe 3.14f
    }

    @Test
    fun `flow emits default then updated value`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", "initial")

        pref.flow.first() shouldBe "initial"

        pref.value("updated")

        pref.flow.first() shouldBe "updated"
    }

    @Test
    fun `update returns old and new values`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", 10)

        val result = pref.update { it + 5 }
        result shouldBe DataStoreValue.Updated(old = 10, new = 15)
        pref.value() shouldBe 15
    }

    @Test
    fun `update transforms from current value`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", "hello")

        pref.value("world")
        val result = pref.update { "$it!" }
        result shouldBe DataStoreValue.Updated(old = "world", new = "world!")
    }

    @Test
    fun `valueBlocking get returns current value`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", "blocking_default")
        pref.valueBlocking shouldBe "blocking_default"

        pref.value("new_blocking")
        pref.valueBlocking shouldBe "new_blocking"
    }

    @Test
    fun `valueBlocking set writes value`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("test_key", 0)
        pref.valueBlocking = 42
        pref.value() shouldBe 42
    }

    @Test
    fun `keyName returns the preference key name`() = runTest2 {
        val ds = createDataStore()
        val pref = ds.createValue("my.special.key", true)
        pref.keyName shouldBe "my.special.key"
    }
}
