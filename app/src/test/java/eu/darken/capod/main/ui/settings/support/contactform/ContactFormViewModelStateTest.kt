package eu.darken.capod.main.ui.settings.support.contactform

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class ContactFormViewModelStateTest : BaseTest() {

    private fun words(count: Int): String = (1..count).joinToString(" ") { "word$it" }

    @Nested
    inner class IsBug {
        @Test
        fun `BUG category returns true`() {
            ContactFormViewModel.State(category = ContactFormViewModel.Category.BUG).isBug shouldBe true
        }

        @Test
        fun `QUESTION category returns false`() {
            ContactFormViewModel.State(category = ContactFormViewModel.Category.QUESTION).isBug shouldBe false
        }

        @Test
        fun `FEATURE category returns false`() {
            ContactFormViewModel.State(category = ContactFormViewModel.Category.FEATURE).isBug shouldBe false
        }
    }

    @Nested
    inner class DescriptionWords {
        @Test
        fun `empty string returns 0`() {
            ContactFormViewModel.State(description = "").descriptionWords shouldBe 0
        }

        @Test
        fun `whitespace only returns 0`() {
            ContactFormViewModel.State(description = "   \t  ").descriptionWords shouldBe 0
        }

        @Test
        fun `counts words correctly`() {
            ContactFormViewModel.State(description = "hello world").descriptionWords shouldBe 2
        }

        @Test
        fun `handles multiple spaces between words`() {
            ContactFormViewModel.State(description = "  hello   world  ").descriptionWords shouldBe 2
        }
    }

    @Nested
    inner class CanSend {
        @Test
        fun `19 description words is not enough`() {
            ContactFormViewModel.State(description = words(19)).canSend shouldBe false
        }

        @Test
        fun `20 description words is enough for non-bug`() {
            ContactFormViewModel.State(
                category = ContactFormViewModel.Category.QUESTION,
                description = words(20),
            ).canSend shouldBe true
        }

        @Test
        fun `bug with 20 desc words but 9 expected words is not enough`() {
            ContactFormViewModel.State(
                category = ContactFormViewModel.Category.BUG,
                description = words(20),
                expectedBehavior = words(9),
            ).canSend shouldBe false
        }

        @Test
        fun `bug with 20 desc words and 10 expected words is enough`() {
            ContactFormViewModel.State(
                category = ContactFormViewModel.Category.BUG,
                description = words(20),
                expectedBehavior = words(10),
            ).canSend shouldBe true
        }

        @Test
        fun `non-bug ignores expectedBehavior`() {
            ContactFormViewModel.State(
                category = ContactFormViewModel.Category.FEATURE,
                description = words(20),
                expectedBehavior = "",
            ).canSend shouldBe true
        }

        @Test
        fun `isSending blocks canSend`() {
            ContactFormViewModel.State(
                description = words(20),
                isSending = true,
            ).canSend shouldBe false
        }

        @Test
        fun `isRecording blocks canSend`() {
            ContactFormViewModel.State(
                description = words(20),
                isRecording = true,
            ).canSend shouldBe false
        }
    }
}
