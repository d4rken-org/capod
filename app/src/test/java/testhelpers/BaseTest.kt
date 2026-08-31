package testhelpers

import eu.darken.capod.common.debug.Bugs
import eu.darken.capod.common.debug.logging.Logging
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterAll
import testhelpers.logging.JUnitLogger


open class BaseTest {
    init {
        Logging.clearAll()
        Logging.install(JUnitLogger())
        // JVM-global and written by anything that starts a debug recording. Reset per test instance
        // and not in a companion teardown: the JUnit 5 @AfterAll below never fires under the JUnit 4
        // Robolectric runner that the recorder tests use.
        Bugs.isDebug.value = false
        testClassName = this.javaClass.simpleName
    }

    companion object {
        private var testClassName: String? = null

        @JvmStatic
        @AfterAll
        fun onTestClassFinished() {
            unmockkAll()
            log(testClassName!!, VERBOSE) { "onTestClassFinished()" }
            Logging.clearAll()
        }
    }
}
