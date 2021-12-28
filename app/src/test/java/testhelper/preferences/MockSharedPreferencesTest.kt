package testhelper.preferences

import androidx.core.content.edit
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelper.BaseTest
import testhelpers.preferences.MockSharedPreferences

class MockSharedPreferencesTest : BaseTest() {

    private fun createInstance() = MockSharedPreferences()

    @Test
    fun `test boolean insertion`() {
        val prefs = createInstance()
        prefs.dataMapPeek shouldBe emptyMap()
        prefs.getBoolean("key", true) shouldBe true
        prefs.edit { putBoolean("key", false) }
        prefs.getBoolean("key", true) shouldBe false
        prefs.dataMapPeek["key"] shouldBe false
    }
}
