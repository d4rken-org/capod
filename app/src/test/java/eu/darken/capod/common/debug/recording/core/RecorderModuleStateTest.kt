package eu.darken.capod.common.debug.recording.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class RecorderModuleStateTest : BaseTest() {

    @Nested
    inner class DefaultState {
        @Test
        fun `shouldRecord is false`() {
            RecorderModule.State().shouldRecord shouldBe false
        }

        @Test
        fun `isRecording is false`() {
            RecorderModule.State().isRecording shouldBe false
        }

        @Test
        fun `currentLogDir is null`() {
            RecorderModule.State().currentLogDir shouldBe null
        }

        @Test
        fun `recordingStartedAt is zero`() {
            RecorderModule.State().recordingStartedAt shouldBe 0L
        }

        @Test
        fun `currentLogPath is null`() {
            RecorderModule.State().currentLogPath shouldBe null
        }
    }
}
