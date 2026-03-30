package eu.darken.capod.debug.l2cap

import eu.darken.capod.common.bluetooth.l2cap.L2capSocketFactory
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class L2capPocActivity : ComponentActivity() {

    companion object {
        private const val TAG = "L2capPoc"
        private const val PSM = 0x1001

        private val HANDSHAKE = byteArrayOf(
            0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        private val ANC_OFF = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0d, 0x01, 0x00, 0x00, 0x00
        )
        private val ANC_ON = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0d, 0x02, 0x00, 0x00, 0x00
        )
        private val TRANSPARENCY = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x0d, 0x03, 0x00, 0x00, 0x00
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) Log.d(TAG, "BLUETOOTH_CONNECT granted")
        else Log.w(TAG, "BLUETOOTH_CONNECT denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PocScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @SuppressLint("MissingPermission")
    @Composable
    private fun PocScreen() {
        val btManager = remember { getSystemService(BluetoothManager::class.java) }
        val adapter: BluetoothAdapter? = remember { btManager?.adapter }
        val logEntries = remember { mutableStateListOf<String>() }
        val logListState = rememberLazyListState()
        var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
        var socket by remember { mutableStateOf<BluetoothSocket?>(null) }
        var outputStream by remember { mutableStateOf<OutputStream?>(null) }
        var connected by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        var readerJob by remember { mutableStateOf<Job?>(null) }

        val timeFmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

        fun log(msg: String) {
            val line = "${timeFmt.format(Date())} $msg"
            Log.d(TAG, msg)
            logEntries.add(line)
        }

        fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it) }

        LaunchedEffect(logEntries.size) {
            if (logEntries.isNotEmpty()) logListState.animateScrollToItem(logEntries.size - 1)
        }

        fun startReader(input: InputStream) {
            readerJob = scope.launch(Dispatchers.IO) {
                val buf = ByteArray(1024)
                try {
                    while (isActive) {
                        val len = input.read(buf)
                        if (len == -1) {
                            launch(Dispatchers.Main) { log("<<< Stream closed by remote") }
                            break
                        }
                        val data = buf.copyOf(len)
                        launch(Dispatchers.Main) { log("<<< [${data.size}] ${data.hex()}") }
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) { log("<<< Read error: ${e.message}") }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("L2CAP PoC — AAP Connection", style = MaterialTheme.typography.titleMedium)

            if (adapter == null) {
                Text("No Bluetooth adapter found", color = MaterialTheme.colorScheme.error)
                return@Column
            }

            // Bonded devices
            if (!connected) {
                Text("Bonded Devices:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                val bonded = remember(adapter) {
                    try { adapter.bondedDevices?.toList() ?: emptyList() }
                    catch (_: SecurityException) { emptyList() }
                }
                bonded.forEach { device ->
                    val name = try { device.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }
                    val isSelected = selectedDevice == device
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { selectedDevice = device },
                        colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
                    ) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            Text("$name  ${device.address}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Connection controls
            FlowRow(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val dev = selectedDevice ?: return@Button
                        log("=== Connecting to ${dev.address} ===")
                        scope.launch(Dispatchers.IO) {
                            try {
                                val sock = L2capSocketFactory().createSocket(dev, PSM)
                                withContext(Dispatchers.Main) { log("Socket created, connecting...") }
                                sock.connect()
                                withContext(Dispatchers.Main) {
                                    log("Connected!")
                                    socket = sock
                                    outputStream = sock.outputStream
                                    connected = true
                                }
                                startReader(sock.inputStream)

                                withContext(Dispatchers.Main) { log(">>> Handshake [${HANDSHAKE.size}] ${HANDSHAKE.hex()}") }
                                sock.outputStream.write(HANDSHAKE)
                                sock.outputStream.flush()
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { log("Failed: ${e::class.simpleName}: ${e.message}") }
                            }
                        }
                    },
                    enabled = selectedDevice != null && !connected
                ) { Text("Connect") }

                Button(
                    onClick = {
                        log("=== Disconnecting ===")
                        readerJob?.cancel()
                        readerJob = null
                        try { socket?.close() } catch (_: Exception) {}
                        socket = null
                        outputStream = null
                        connected = false
                    },
                    enabled = connected
                ) { Text("Disconnect") }
            }

            // AAP command buttons
            if (connected) {
                FlowRow(modifier = Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fun sendCommand(name: String, cmd: ByteArray) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                withContext(Dispatchers.Main) { log(">>> $name [${cmd.size}] ${cmd.hex()}") }
                                outputStream?.write(cmd)
                                outputStream?.flush()
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { log(">>> Send error: ${e.message}") }
                            }
                        }
                    }
                    Button(onClick = { sendCommand("ANC Off", ANC_OFF) }) { Text("ANC Off") }
                    Button(onClick = { sendCommand("ANC On", ANC_ON) }) { Text("ANC On") }
                    Button(onClick = { sendCommand("Transparency", TRANSPARENCY) }) { Text("Transparency") }
                    Button(onClick = { sendCommand("Handshake", HANDSHAKE) }) { Text("Handshake") }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            LazyColumn(state = logListState, modifier = Modifier.weight(1f)) {
                items(logEntries) { entry ->
                    Text(text = entry, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}
