package eu.darken.capod.common.review

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import eu.darken.capod.common.datastore.value
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ReviewSettingsTest : BaseTest() {

    private val json = Json { ignoreUnknownKeys = true }

    // One test method on purpose: ReviewSettings is a @Singleton whose DataStore is bound to the
    // Context property delegate, and DataStore forbids two active instances on the same file.
    @Test
    fun `the review timestamps round-trip through the real DataStore`() = runTest {
        // Real time and real I/O: the DataStore does its work off the test scheduler.
        withContext(Dispatchers.IO) {
            val context = ApplicationProvider.getApplicationContext<Context>()

            // Real DataStore, no mocks: this catches a mismatch between what the explicit
            // Instant serializer writes and what the reader expects to find.
            val settings = ReviewSettings(context, json)

            settings.lastDismissed.value() shouldBe null
            settings.reviewedAt.value() shouldBe null

            // Millisecond granularity, that is all InstantEpochMillisSerializer preserves.
            val dismissedAt = Instant.ofEpochMilli(1_700_000_000_000L)
            val reviewedAt = Instant.ofEpochMilli(1_700_000_123_456L)

            settings.lastDismissed.value(dismissedAt)
            settings.reviewedAt.value(reviewedAt)

            settings.lastDismissed.value() shouldBe dismissedAt
            settings.reviewedAt.value() shouldBe reviewedAt

            // Writing null clears the key instead of storing a literal "null" that would then be
            // decoded on the next read.
            settings.lastDismissed.value(null)
            settings.lastDismissed.value() shouldBe null
            settings.dataStore.data.first().contains(DISMISSED_KEY) shouldBe false

            // onErrorFallbackToDefault is off, so corrupt data surfaces instead of silently
            // resetting the snooze/reviewed bookkeeping to "never".
            settings.dataStore.edit { it[REVIEWED_KEY] = "not-a-timestamp" }
            shouldThrow<SerializationException> { settings.reviewedAt.value() }
        }
    }

    companion object {
        private val DISMISSED_KEY = stringPreferencesKey("review.dismissedAt")
        private val REVIEWED_KEY = stringPreferencesKey("review.reviewedAt")
    }
}
