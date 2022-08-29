package eu.darken.capod.common.flow

import eu.darken.capod.common.collections.mutate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.runTest2
import testhelpers.flow.test
import java.io.IOException
import java.lang.Thread.sleep
import kotlin.concurrent.thread
import kotlin.coroutines.EmptyCoroutineContext

class DynamicStateFlowTest : BaseTest() {

    // Without an init value, there isn't a way to keep using the flow
    @Test
    fun `exceptions on initialization are rethrown`() {
        val testScope =
            createTestCoroutineScope(TestCoroutineDispatcher() + TestCoroutineExceptionHandler() + EmptyCoroutineContext)
        val hotData = eu.darken.capod.common.flow.DynamicStateFlow<String>(
            loggingTag = "tag",
            parentScope = testScope,
            coroutineContext = Dispatchers.Unconfined,
            startValueProvider = { throw IOException() }
        )
        runBlocking {
            withTimeoutOrNull(500) {
                // This blocking scope gets the init exception as the first caller
                hotData.flow.firstOrNull()
            } shouldBe null
        }

        testScope.advanceUntilIdle()

        testScope.uncaughtExceptions.single() shouldBe instanceOf(IOException::class)
    }

    @Test
    fun `subscription doesn't end when no subscriber is collecting, mode Lazily`() {
        val testScope =
            createTestCoroutineScope(TestCoroutineDispatcher() + TestCoroutineExceptionHandler() + EmptyCoroutineContext)
        val valueProvider = mockk<suspend CoroutineScope.() -> String>()
        coEvery { valueProvider.invoke(any()) } returns "Test"

        val hotData = eu.darken.capod.common.flow.DynamicStateFlow(
            loggingTag = "tag",
            parentScope = testScope,
            coroutineContext = Dispatchers.Unconfined,
            startValueProvider = valueProvider,
        )

        testScope.apply {
            runTest2(autoCancel = true) {
                hotData.flow.first() shouldBe "Test"
                hotData.flow.first() shouldBe "Test"
            }
            coVerify(exactly = 1) { valueProvider.invoke(any()) }
        }
    }

    @Test
    fun `value updates`() {
        val testScope =
            createTestCoroutineScope(TestCoroutineDispatcher() + TestCoroutineExceptionHandler() + EmptyCoroutineContext)
        val valueProvider = mockk<suspend CoroutineScope.() -> Long>()
        coEvery { valueProvider.invoke(any()) } returns 1

        val hotData = eu.darken.capod.common.flow.DynamicStateFlow(
            loggingTag = "tag",
            parentScope = testScope,
            startValueProvider = valueProvider,
        )

        val testCollector = hotData.flow.test(scope = testScope)
        testCollector.silent = true

        (1..16).forEach { _ ->
            thread {
                (1..200).forEach { _ ->
                    sleep(10)
                    hotData.updateAsync(
                        onUpdate = { this + 1L },
                        onError = { throw it }
                    )
                }
            }
        }

        runBlocking {
            testCollector.await { list, _ -> list.size == 3201 }
            testCollector.latestValues shouldBe (1..3201).toList()
        }

        coVerify(exactly = 1) { valueProvider.invoke(any()) }
    }

    data class TestData(
        val number: Long = 1
    )

    @Test
    fun `check multi threading value updates with more complex data`() {
        val testScope =
            createTestCoroutineScope(TestCoroutineDispatcher() + TestCoroutineExceptionHandler() + EmptyCoroutineContext)
        val valueProvider =
            mockk<suspend CoroutineScope.() -> Map<String, eu.darken.capod.common.flow.DynamicStateFlowTest.TestData>>()
        coEvery { valueProvider.invoke(any()) } returns mapOf("data" to eu.darken.capod.common.flow.DynamicStateFlowTest.TestData())

        val hotData = eu.darken.capod.common.flow.DynamicStateFlow(
            loggingTag = "tag",
            parentScope = testScope,
            startValueProvider = valueProvider,
        )

        val testCollector = hotData.flow.test(scope = testScope)
        testCollector.silent = true

        (1..10).forEach { _ ->
            thread {
                (1..400).forEach { _ ->
                    hotData.updateAsync {
                        mutate {
                            this["data"] = getValue("data").copy(
                                number = getValue("data").number + 1
                            )
                        }
                    }
                }
            }
        }

        runBlocking {
            testCollector.await { list, _ -> list.size == 4001 }
            testCollector.latestValues.map { it.values.single().number } shouldBe (1L..4001L).toList()
        }

        coVerify(exactly = 1) { valueProvider.invoke(any()) }
    }

    @Test
    fun `only emit new values if they actually changed updates`() {
        val testScope =
            createTestCoroutineScope(TestCoroutineDispatcher() + TestCoroutineExceptionHandler() + EmptyCoroutineContext)

        val hotData = eu.darken.capod.common.flow.DynamicStateFlow(
            loggingTag = "tag",
            parentScope = testScope,
            startValueProvider = { "1" },
        )

        val testCollector = hotData.flow.test(scope = testScope)
        testCollector.silent = true

        hotData.updateAsync { "1" }
        hotData.updateAsync { "2" }
        hotData.updateAsync { "2" }
        hotData.updateAsync { "1" }

        runBlocking {
            testCollector.await { list, _ -> list.size == 3 }
            testCollector.latestValues shouldBe listOf("1", "2", "1")
        }
    }

    @Test
    fun `multiple subscribers share the flow`() = runTest2(autoCancel = true) {
        val valueProvider = mockk<suspend CoroutineScope.() -> String>()
        coEvery { valueProvider.invoke(any()) } returns "Test"

        val hotData = eu.darken.capod.common.flow.DynamicStateFlow(
            loggingTag = "tag",
            parentScope = this,
            startValueProvider = valueProvider,
        )

        val sub1 = hotData.flow.test(tag = "sub1", scope = this)
        val sub2 = hotData.flow.test(tag = "sub2", scope = this)
        val sub3 = hotData.flow.test(tag = "sub3", scope = this)

        hotData.updateAsync { "A" }
        hotData.updateAsync { "B" }
        hotData.updateAsync { "C" }

        advanceUntilIdle()

        listOf(sub1, sub2, sub3).forEach {
            it.await { list, _ -> list.size == 4 }
            it.latestValues shouldBe listOf("Test", "A", "B", "C")
            it.cancelAndJoin()
        }

        hotData.flow.first() shouldBe "C"

        coVerify(exactly = 1) { valueProvider.invoke(any()) }
    }

    @Test
    fun `value is persisted between unsubscribes`() = runTest2(autoCancel = true) {
        val valueProvider = mockk<suspend CoroutineScope.() -> Long>()
        coEvery { valueProvider.invoke(any()) } returns 1

        val hotData = eu.darken.capod.common.flow.DynamicStateFlow(
            loggingTag = "tag",
            parentScope = this,
            coroutineContext = this.coroutineContext,
            startValueProvider = valueProvider,
        )

        val testCollector1 = hotData.flow.test(tag = "collector1", scope = this)
        testCollector1.silent = false

        (1..10).forEach { _ ->
            hotData.updateAsync {
                this + 1L
            }
        }

        advanceUntilIdle()

        testCollector1.await { list, _ -> list.size == 11 }
        testCollector1.latestValues shouldBe (1L..11L).toList()

        testCollector1.cancelAndJoin()

        val testCollector2 = hotData.flow.test(tag = "collector2", scope = this)
        testCollector2.silent = false

        advanceUntilIdle()

        testCollector2.cancelAndJoin()

        testCollector2.latestValues shouldBe listOf(11L)

        coVerify(exactly = 1) { valueProvider.invoke(any()) }
    }

    @Test
    fun `blocking update is actually blocking`() = runBlocking {
        val testScope =
            createTestCoroutineScope(TestCoroutineDispatcher() + TestCoroutineExceptionHandler() + EmptyCoroutineContext)
        val hotData = eu.darken.capod.common.flow.DynamicStateFlow(
            loggingTag = "tag",
            parentScope = testScope,
            coroutineContext = testScope.coroutineContext,
            startValueProvider = {
                delay(2000)
                2
            },
        )

        hotData.updateAsync {
            delay(2000)
            this + 1
        }

        val testCollector = hotData.flow.test(scope = testScope)

        testScope.advanceUntilIdle()

        hotData.updateBlocking { this - 3 } shouldBe 0

        testCollector.await { _, i -> i == 3 }
        testCollector.latestValues shouldBe listOf(2, 3, 0)

        testCollector.cancelAndJoin()
    }

    @Test
    fun `blocking update rethrows error`() = runBlocking {
        val testScope =
            createTestCoroutineScope(TestCoroutineDispatcher() + TestCoroutineExceptionHandler() + EmptyCoroutineContext)
        val hotData = eu.darken.capod.common.flow.DynamicStateFlow(
            loggingTag = "tag",
            parentScope = testScope,
            coroutineContext = testScope.coroutineContext,
            startValueProvider = {
                delay(2000)
                2
            },
        )

        val testCollector = hotData.flow.test(scope = testScope)

        testScope.advanceUntilIdle()

        shouldThrow<IOException> {
            hotData.updateBlocking { throw IOException("Surprise") } shouldBe 0
        }
        hotData.flow.first() shouldBe 2

        hotData.updateBlocking { 3 } shouldBe 3
        hotData.flow.first() shouldBe 3

        testScope.uncaughtExceptions.singleOrNull() shouldBe null

        testCollector.cancelAndJoin()
    }

    @Test
    fun `async updates error handler`() = runTest2(expectedError = IOException::class) {
        val hotData = eu.darken.capod.common.flow.DynamicStateFlow(
            loggingTag = "tag",
            parentScope = this,
            startValueProvider = { 1 },
        )

        val testCollector = hotData.flow.test(scope = this)
        advanceUntilIdle()

        hotData.updateAsync { throw IOException("Surprise") }

        advanceUntilIdle()
    }

    @Test
    fun `async updates rethrow errors on HotDataFlow scope if no error handler is set`() = runBlocking {
        val testScope =
            createTestCoroutineScope(TestCoroutineDispatcher() + TestCoroutineExceptionHandler() + EmptyCoroutineContext)

        val hotData = eu.darken.capod.common.flow.DynamicStateFlow(
            loggingTag = "tag",
            parentScope = testScope,
            startValueProvider = { 1 },
        )

        val testCollector = hotData.flow.test(scope = testScope)
        testScope.advanceUntilIdle()

        var thrownError: Exception? = null

        hotData.updateAsync(
            onUpdate = { throw IOException("Surprise") },
            onError = { thrownError = it }
        )

        testScope.advanceUntilIdle()
        thrownError!!.shouldBeInstanceOf<IOException>()
        testScope.uncaughtExceptions.singleOrNull() shouldBe null

        testCollector.cancelAndJoin()
    }

    @Test
    fun `clean up function is called when parent scope is cancelled`() = runTest {
        val testScope =
            createTestCoroutineScope(TestCoroutineDispatcher() + TestCoroutineExceptionHandler() + EmptyCoroutineContext)

        var onReleaseValue: String? = null

        val hotData = eu.darken.capod.common.flow.DynamicStateFlow(
            loggingTag = "tag",
            parentScope = testScope,
            coroutineContext = Dispatchers.Unconfined,
            startValueProvider = { "Test" },
            onRelease = {
                onReleaseValue = it
            }
        )

        hotData.flow.first() shouldBe "Test"

        testScope.cancel()

        onReleaseValue shouldBe "Test"
    }
}