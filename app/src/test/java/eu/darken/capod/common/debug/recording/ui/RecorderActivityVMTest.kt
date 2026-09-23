package eu.darken.capod.common.debug.recording.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import eu.darken.capod.common.WebpageTool
import eu.darken.capod.common.debug.recording.core.DebugSessionManager
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.IOException

class RecorderActivityVMTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()
    private var vm: RecorderActivityVM? = null

    private lateinit var sessionManager: DebugSessionManager

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sessionManager = mockk<DebugSessionManager>(relaxed = true).also {
            every { it.sessions } returns flowOf(emptyList())
        }
    }

    @AfterEach
    fun teardown() {
        vm?.vmScope?.cancel()
        vm = null
        Dispatchers.resetMain()
    }

    private fun createVM() = RecorderActivityVM(
        handle = SavedStateHandle(mapOf(RecorderActivity.RECORD_SESSION_ID to "session-1")),
        dispatcherProvider = TestDispatcherProvider(),
        context = mockk<Context>(relaxed = true),
        sessionManager = sessionManager,
        webpageTool = mockk<WebpageTool>(relaxed = true),
    ).also { vm = it }

    @Test
    fun `share failure is surfaced as an error event and launches no chooser`() = runTest(testDispatcher) {
        val error = IOException("zip failed")
        coEvery { sessionManager.getZipUri("session-1") } throws error

        val vm = createVM()
        vm.share()

        vm.errorEvents.first() shouldBe error
        withTimeoutOrNull(100) { vm.events.first() }.shouldBeNull()
        vm.state.first().isWorking shouldBe false
    }
}
