package eu.darken.capod.monitor.core

import eu.darken.capod.common.bluetooth.BleScanner
import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import eu.darken.capod.pods.core.PodDevice
import eu.darken.capod.pods.core.PodFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodMonitor @Inject constructor(
    private val bleScanner: BleScanner,
    private val podFactory: PodFactory,
) {

    val pods: Flow<List<PodDevice>> = bleScanner.scan()
        .map { result ->
            // For each address we only want the newest result, upstream may batch data
            result.groupBy { it.device.address }
                .values
                .map { sameAdrDevs ->
                    val newest = sameAdrDevs.maxByOrNull { it.timestampNanos }!!
                    log(TAG, VERBOSE) { "Discarding stale results: ${sameAdrDevs.minus(newest)}" }
                    newest
                }
        }
        .onStart { emptyList<PodDevice>() }
        .map { scanResults ->
           val pods = scanResults
               .sortedByDescending { it.rssi }
               .mapNotNull { podFactory.createPod(it) }

//            if (BuildConfigWrap.DEBUG && scanResults.isNotEmpty()) {
//               val fake1 = AirPodsMax(
//                    identifier = UUID.fromString("63436171-5433-4506-8bf5-faf6c35ffa8a"),
//                    scanResult = scanResults.first(),
//                    proximityMessage = ProximityPairing.Decoder().decode(
//                        message = ContinuityProtocol.Message(
//                            type = 0x07.toUByte(),
//                            length = 25,
//                            data = arrayOf<Byte>(1, 10, 32, 98, 4, -128, 1, 15, 64, 13, 112, 80, 22, -14, 64, -125, 22, -65, 16, 22, 52, -101, 116, -124, -24).toByteArray().toUByteArray()
//                        )
//                    )!!
//                )
//                val fake2 = BeatsFlex(
//                    identifier = UUID.fromString("e563098d-ea24-4136-a169-5a59344317c3"),
//                    scanResult = scanResults.first(),
//                    proximityMessage = ProximityPairing.Decoder().decode(
//                        message = ContinuityProtocol.Message(
//                            type = 0x07.toUByte(),
//                            length = 25,
//                            data = arrayOf<Byte>(1, 16, 32, 10, -12, -113, 0, 1, 0, -60, 113, -97, -100, -17, -94, -29, -70, 102, -2, 29, 69, -97, -55, 47, -96).toByteArray().toUByteArray()
//                        )
//                    )!!
//                )
//                pods.plus(fake1).plus(fake2)
//            } else {
//                pods
//            }

            pods
        }

    companion object {
        private val TAG = logTag("Monitor", "PodMonitor")
    }
}