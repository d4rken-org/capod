package eu.darken.capod.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.Flow

@Composable
fun <T> waitForState(flow: Flow<T>): State<T?> {
    return produceState(initialValue = null) {
        flow.collect { value = it }
    }
}
