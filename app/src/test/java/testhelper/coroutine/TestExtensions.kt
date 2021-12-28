package testhelper.coroutine

import eu.darken.cap.common.debug.logging.log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.UncompletedCoroutinesError
import kotlinx.coroutines.test.runBlockingTest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * If you have a test that uses a coroutine that never stops, you may use this.
 */

@ExperimentalCoroutinesApi // Since 1.2.1, tentatively till 1.3.0
fun TestCoroutineScope.runBlockingTest2(
    allowUncompleted: Boolean = false,
    block: suspend TestCoroutineScope.() -> Unit
): Unit = runBlockingTest2(
    allowUncompleted = allowUncompleted,
    context = coroutineContext,
    testBody = block
)

fun runBlockingTest2(
    allowUncompleted: Boolean = false,
    context: CoroutineContext = EmptyCoroutineContext,
    testBody: suspend TestCoroutineScope.() -> Unit
) {
    try {
        runBlocking {
            try {
                runBlockingTest(
                    context = context,
                    testBody = testBody
                )
            } catch (e: UncompletedCoroutinesError) {
                if (!allowUncompleted) throw e
                else log { "Ignoring active job." }
            }
        }
    } catch (e: Exception) {
        if (!allowUncompleted || (e.message != "This job has not completed yet")) {
            throw e
        }
    }
}

