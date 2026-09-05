package eu.darken.capod.common.bluetooth

import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag

suspend fun Collection<BleScanResult>.onlyNewAndUnique(): List<BleScanResult> = this
    .groupBy { it.address }
    .values
    .map { sameAdrDevs ->
        // For each address we only want the newest result, upstream may batch data
        val newest = sameAdrDevs.maxByOrNull { it.generatedAtNanos }!!
        sameAdrDevs.minus(newest).let {
            if (it.isNotEmpty()) log(TAG, VERBOSE) { "Discarding stale results: ${it.logSummary()}" }
        }
        newest
    }

private val TAG = logTag("Bluetooth", "Scanner")
