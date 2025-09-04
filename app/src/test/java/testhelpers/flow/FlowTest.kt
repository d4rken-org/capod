package testhelpers.flow

import eu.darken.capod.common.debug.logging.Logging.Priority.WARN
import eu.darken.capod.common.debug.logging.asLog
import eu.darken.capod.common.debug.logging.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

fun <T> Flow<T>.test(
    tag: String? = null,
    scope: CoroutineScope
): TestCollector<T> = createTest(tag ?: "FlowTest").start(scope = scope)

fun <T> Flow<T>.createTest(
    tag: String? = null
): TestCollector<T> = TestCollector(this, tag ?: "FlowTest")

class TestCollector<T>(
    private val flow: Flow<T>,
    private val tag: String

) {
    private var error: Throwable? = null
    private lateinit var job: Job
    private val cache = MutableSharedFlow<T>(
        replay = Int.MAX_VALUE,
        extraBufferCapacity = Int.MAX_VALUE,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    private var latestInternal: T? = null
    private val collectedValuesMutex = Mutex()
    private val collectedValues = mutableListOf<T>()

    var silent = false

    fun start(scope: CoroutineScope) = apply {
        flow
            .buffer(capacity = Int.MAX_VALUE)
            .onStart { log(tag) { "Setting up." } }
            .onCompletion { log(tag) { "Final." } }
            .onEach {
                collectedValuesMutex.withLock {
                    if (!silent) log(tag) { "Collecting: $it" }
                    latestInternal = it
                    collectedValues.add(it)
                    cache.emit(it)
                }
            }
            .catch { e ->
                log(tag, WARN) { "Caught error: ${e.asLog()}" }
                error = e
            }
            .launchIn(scope)
            .also { job = it }
    }

    fun emissions(): Flow<T> = cache

    val latestValue: T?
        get() = collectedValues.last()

    val latestValues: List<T>
        get() = collectedValues

    fun await(
        timeout: Long = 10_000,
        condition: (List<T>, T) -> Boolean
    ): T = runBlocking {
        withTimeout(timeMillis = timeout) {
            emissions().first {
                condition(collectedValues, it)
            }
        }
    }

    suspend fun awaitFinal(cancel: Boolean = false) = apply {
        if (cancel) job.cancel()
        try {
            job.join()
        } catch (e: Exception) {
            error = e
        }
    }

    suspend fun assertNoErrors() = apply {
        awaitFinal()
        require(error == null) { "Error was not null: $error" }
    }

    suspend fun cancelAndJoin() {
        if (job.isCompleted) throw IllegalStateException("Flow is already canceled.")

        job.cancelAndJoin()
    }
}