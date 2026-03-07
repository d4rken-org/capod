package testhelpers.datastore

import eu.darken.capod.common.datastore.DataStoreValue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDataStoreValue<T>(initial: T) {
    private val _flow = MutableStateFlow(initial)

    val mock: DataStoreValue<T> = mockk<DataStoreValue<T>>().also { m ->
        every { m.flow } returns _flow
        coEvery { m.update(any()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val transform = firstArg<(T) -> T>()
            val old = _flow.value
            val new = transform(old)
            _flow.value = new
            DataStoreValue.Updated(old, new)
        }
    }

    var value: T
        get() = _flow.value
        set(v) {
            _flow.value = v
        }
}
