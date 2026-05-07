package eu.darken.capod.common.bluetooth

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.datastore.FakeDataStoreValue

class NudgeCapabilityStoreTest : BaseTest() {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var fakePersisted: FakeDataStoreValue<NudgeAvailability>

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakePersisted = FakeDataStoreValue(NudgeAvailability.UNKNOWN)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createStore() = NudgeCapabilityStore(
        persistedValue = fakePersisted.mock,
        appScope = kotlinx.coroutines.CoroutineScope(testDispatcher),
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
    )

    @Test
    fun `verdictFor maps Accepted to AVAILABLE`() {
        NudgeCapabilityStore.verdictFor(NudgeAttemptResult.Accepted) shouldBe NudgeAvailability.AVAILABLE
    }

    @Test
    fun `verdictFor maps UnavailableHiddenApi to BROKEN`() {
        NudgeCapabilityStore.verdictFor(NudgeAttemptResult.UnavailableHiddenApi) shouldBe NudgeAvailability.BROKEN
    }

    @Test
    fun `verdictFor returns null for Rejected`() {
        NudgeCapabilityStore.verdictFor(NudgeAttemptResult.Rejected) shouldBe null
    }

    @Test
    fun `verdictFor returns null for UnavailableMissingPermission`() {
        NudgeCapabilityStore.verdictFor(NudgeAttemptResult.UnavailableMissingPermission) shouldBe null
    }

    @Test
    fun `record Accepted persists AVAILABLE`() = runTest(testDispatcher) {
        val store = createStore()

        store.record(NudgeAttemptResult.Accepted)

        fakePersisted.value shouldBe NudgeAvailability.AVAILABLE
        store.availability.value shouldBe NudgeAvailability.AVAILABLE
    }

    @Test
    fun `record UnavailableHiddenApi persists BROKEN`() = runTest(testDispatcher) {
        val store = createStore()

        store.record(NudgeAttemptResult.UnavailableHiddenApi)

        fakePersisted.value shouldBe NudgeAvailability.BROKEN
    }

    @Test
    fun `record Rejected does not persist (stays UNKNOWN)`() = runTest(testDispatcher) {
        val store = createStore()

        store.record(NudgeAttemptResult.Rejected)

        fakePersisted.value shouldBe NudgeAvailability.UNKNOWN
        store.availability.value shouldBe NudgeAvailability.UNKNOWN
    }

    @Test
    fun `record UnavailableMissingPermission does not persist`() = runTest(testDispatcher) {
        // Pre-existing AVAILABLE — a transient permission failure should not flip it to BROKEN.
        fakePersisted.value = NudgeAvailability.AVAILABLE
        val store = createStore()

        store.record(NudgeAttemptResult.UnavailableMissingPermission)

        fakePersisted.value shouldBe NudgeAvailability.AVAILABLE
    }

    @Test
    fun `availability StateFlow reflects persisted value on construction`() = runTest(testDispatcher) {
        fakePersisted.value = NudgeAvailability.BROKEN

        val store = createStore()

        store.availability.value shouldBe NudgeAvailability.BROKEN
    }

    @Test
    fun `availability StateFlow tracks persisted updates`() = runTest(testDispatcher) {
        val store = createStore()
        store.availability.value shouldBe NudgeAvailability.UNKNOWN

        fakePersisted.value = NudgeAvailability.AVAILABLE

        store.availability.value shouldBe NudgeAvailability.AVAILABLE
    }
}
