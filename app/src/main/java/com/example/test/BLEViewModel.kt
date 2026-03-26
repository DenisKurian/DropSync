package com.example.test

import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

class BLEViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BLEViewModel"
        private const val RETRY_DELAY_MS = 3000L
        private const val GENERAL_TIMEOUT_MS = 45000L // 45 seconds to escape deadlocks
    }

    private enum class TransferState {
        IDLE,
        WAITING_FOR_REPLY,
        CONNECTING_WIFI,
        CREATING_GROUP,
        RECEIVING,
        SENDING,
        DISCONNECTING
    }

    private val neighborTable = NeighborTable()
    private val bleAdvertiser = BLEAdvertiser(application)
    private val selfNodeId = MeshIdentity.getNodeId(application)
    private val wifi = WifiDirectController(application)

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices

    private val _receivedFileUri = MutableStateFlow<String?>(null)
    val receivedFileUri: StateFlow<String?> = _receivedFileUri

    private var pendingFileUri: Uri? = null
    private var targetNodeId: Int? = null

    private var state = TransferState.IDLE
    private var isConnecting = false

    private val btManager = application.getSystemService(BluetoothManager::class.java)
    private val adapter = btManager?.adapter
    private val scanner = adapter?.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { handleTimeout() }

    private var scanning = false

    init {

        wifi.onConnected = { host ->
            Log.d(TAG, "WiFi connected callback in state=$state to host: $host")

            val uri = pendingFileUri
            if (uri != null && state == TransferState.CONNECTING_WIFI) {
                state = TransferState.SENDING
                scheduleTimeout()

                Thread {
                    try {
                        Log.d(TAG, "WiFi connected → Client starting file send to $host")

                        val client = FileClient(getApplication())
                        val success = client.sendFile(uri, host)

                        Log.d(TAG, "File send result: $success")

                    } catch(e: Exception) {
                        Log.e(TAG, "Failed to send file", e)
                    } finally {
                        Log.d(TAG, "File send workflow complete. Delaying teardown by 3000ms to ensure OS Kernel TCP RAM buffers fully drain over the physical radio interface.")
                        pendingFileUri = null
                        handler.postDelayed({ finishTransferAndTearDown() }, 3000L)
                    }
                }.start()
            } else {
                Log.d(TAG, "Ignoring onConnected because no pending file URI exists or state is wrong")
            }
        }

        wifi.onGroupOwner = {
            Log.d(TAG, "Device became GO → Waiting for FileClient to connect")
            state = TransferState.RECEIVING
            scheduleTimeout()
            
            // Ensure server is started up and listening when GO group activates
            FileServer.startServer(getApplication())
        }

        wifi.onDisconnected = {
            Log.d(TAG, "WiFi disconnected callback triggered")
            if (state == TransferState.DISCONNECTING) {
                resetWorkflow()
            }
        }

        wifi.onConnectionFailed = {
            Log.d(TAG, "WiFi connection failed callback")
            finishTransferAndTearDown()
        }

        FileServer.onFileReceived = { uri ->
            Log.d(TAG, "File received cleanly → $uri")
            handler.post {
                _receivedFileUri.value = uri
            }
            
            handler.postDelayed({
                finishTransferAndTearDown()
            }, 1000L) 
        }

        wifi.onPeersAvailable = { peers ->
            Log.d(TAG, "Peers available: ${peers.size}")

            if (state == TransferState.CONNECTING_WIFI && !isConnecting) {
                if (peers.isNotEmpty()) {
                    val device = peers.first()
                    Log.d(TAG, "Connecting to: ${device.deviceName}")
                    isConnecting = true
                    wifi.connect(device)
                } else {
                    Log.d(TAG, "No peers found")
                }
            } else {
                Log.d(TAG, "Ignoring peers in state=$state isConnecting=$isConnecting")
            }
        }
    }

    private fun scheduleTimeout() {
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, GENERAL_TIMEOUT_MS)
    }

    private fun handleTimeout() {
        if (state != TransferState.IDLE && state != TransferState.DISCONNECTING) {
            Log.e(TAG, "Operation Timed Out! State was stuck at $state for $GENERAL_TIMEOUT_MS ms. Tearing down.")
            finishTransferAndTearDown()
        }
    }

    private fun finishTransferAndTearDown() {
        handler.removeCallbacks(timeoutRunnable)
        if (state == TransferState.DISCONNECTING) return
        state = TransferState.DISCONNECTING
        Log.d(TAG, "finishTransferAndTearDown - Stop Server and Disconnect WiFi Direct")
        FileServer.stopServer()
        wifi.disconnect()
        
        handler.postDelayed({
            if (state == TransferState.DISCONNECTING) resetWorkflow()
        }, 3000L)
    }

    private fun resetWorkflow() {
        Log.d(TAG, "Workflow completely reset. Ready for next transfer.")
        handler.removeCallbacks(timeoutRunnable)
        state = TransferState.IDLE
        isConnecting = false
        targetNodeId = null
        wifi.resetState()
        FileServer.stopServer()
        pendingFileUri = null
        bleAdvertiser.resumeHello()
    }

    fun setPendingFile(uri: Uri) {
        pendingFileUri = uri
    }

    fun startAdvertising() {
        bleAdvertiser.startHelloLoop()
    }

    fun startFileTransfer(destNodeId: Int) {
        if (!wifi.isReady() || state != TransferState.IDLE) {
            Log.d(TAG, "WiFi Direct not ready yet or busy (state=$state). Retrying transfer to node: $destNodeId")

            // Aggressive teardown request if stuck
            if (state != TransferState.IDLE && !wifi.isReady()) {
               wifi.disconnect()
            } else if (state != TransferState.IDLE) {
               Log.e(TAG, "Force resetting workflow because we are stuck in $state")
               resetWorkflow() 
            }

            handler.postDelayed({
                startFileTransfer(destNodeId)
            }, RETRY_DELAY_MS)

            return
        }

        targetNodeId = destNodeId
        state = TransferState.WAITING_FOR_REPLY
        scheduleTimeout()

        Log.d(TAG, "Starting transfer to node: $destNodeId")

        bleAdvertiser.pauseHello()

        val packet = MeshPacket(
            version = BLEConstants.PROTOCOL_VERSION,
            type = BLEConstants.PACKET_TYPE_FILE_REQUEST,
            packetId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            srcNodeId = selfNodeId,
            destNodeId = destNodeId,
            ttl = BLEConstants.DEFAULT_TTL,
            payload = byteArrayOf()
        )

        repeat(3) { index ->
            handler.postDelayed({
                bleAdvertiser.sendRawPacket(packet)
            }, index * 900L)
        }
    }

    fun startScan() {

        if (scanning) return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filter = ScanFilter.Builder()
            .setManufacturerData(BLEConstants.MANUFACTURER_ID, byteArrayOf())
            .build()

        val callback = object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult) {

                val record = result.scanRecord ?: return
                val data = record.manufacturerSpecificData[BLEConstants.MANUFACTURER_ID] ?: return
                val packet = MeshPacket.fromBytes(data) ?: return

                if (packet.srcNodeId == selfNodeId) return

                when (packet.type) {

                    BLEConstants.PACKET_TYPE_HELLO -> {
                        val name = packet.payload.toString(Charsets.UTF_8)

                        neighborTable.update(
                            nodeId = packet.srcNodeId,
                            address = result.device.address ?: "",
                            rssi = result.rssi,
                            name = name
                        )

                        updateUi()
                    }

                    BLEConstants.PACKET_TYPE_FILE_REQUEST -> {
                        if (packet.destNodeId == selfNodeId && state == TransferState.IDLE) {
                            Log.d(TAG, "FILE_REQUEST → becoming GO")

                            state = TransferState.CREATING_GROUP
                            scheduleTimeout()
                            bleAdvertiser.pauseHello()

                            val reply = MeshPacket(
                                version = BLEConstants.PROTOCOL_VERSION,
                                type = BLEConstants.PACKET_TYPE_ROUTE_REPLY,
                                packetId = packet.packetId,
                                srcNodeId = selfNodeId,
                                destNodeId = packet.srcNodeId,
                                ttl = BLEConstants.DEFAULT_TTL,
                                payload = byteArrayOf()
                            )

                            repeat(3) { index ->
                                handler.postDelayed({
                                    bleAdvertiser.sendRawPacket(reply)
                                }, index * 900L)
                            }

                            handler.postDelayed({
                                wifi.createGroup()
                            }, 500L)
                        }
                    }

                    BLEConstants.PACKET_TYPE_ROUTE_REPLY -> {
                        if (packet.destNodeId == selfNodeId && state == TransferState.WAITING_FOR_REPLY) {
                            Log.d(TAG, "ROUTE_REPLY received → discovering peers")
                            state = TransferState.CONNECTING_WIFI
                            scheduleTimeout()

                            handler.postDelayed({
                                wifi.discoverPeers()
                            }, 2800L) 
                        }
                    }
                }
            }
        }

        scanner?.startScan(listOf(filter), settings, callback)
        scanning = true
    }

    private fun updateUi() {
        _devices.value = neighborTable.getAll().map {
            ScannedDevice(
                nodeId = it.nodeId,
                address = it.address,
                name = it.name,
                rssi = it.rssi
            )
        }
    }
}