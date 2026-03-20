package com.example.test

import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
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
    }

    private val neighborTable = NeighborTable()
    private val bleAdvertiser = BLEAdvertiser(application)
    private val selfNodeId = MeshIdentity.getNodeId(application)

    private val wifi = WifiDirectController(application)

    private val seenPackets = ConcurrentHashMap<String, Long>()

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices

    private val _receivedFileUri = MutableStateFlow<String?>(null)
    val receivedFileUri: StateFlow<String?> = _receivedFileUri

    private var pendingFileUri: Uri? = null

    private var targetNodeId: Int? = null
    private var isConnecting = false

    private val btManager = application.getSystemService(BluetoothManager::class.java)
    private val adapter = btManager?.adapter
    private val scanner = adapter?.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())

    private var scanning = false
    private var scanCallback: ScanCallback? = null

    init {

        // 🔵 CLIENT SIDE
        wifi.onConnected = { host ->

            Log.d(TAG, "WiFi connected → sending file to $host")

            pendingFileUri?.let { uri ->

                Thread {

                    val client = FileClient(getApplication())
                    val success = client.sendFile(uri, host)

                    Log.d(TAG, "File send result: $success")

                    wifi.disconnect()
                    isConnecting = false

                }.start()
            }
        }

        // 🟢 GO SIDE
        wifi.onGroupOwner = {
            Log.d(TAG, "Device became GO → waiting for file")
        }

        // 🟡 FILE RECEIVED
        FileServer.onFileReceived = { uri ->
            Log.d(TAG, "File received → $uri")
            _receivedFileUri.value = uri
        }

        // 🔴 FIXED: no labeled return
        wifi.onPeersAvailable = { peers ->

            Log.d(TAG, "Peers available: ${peers.size}")

            if (!isConnecting) {

                val device = peers.firstOrNull()

                if (device != null) {

                    Log.d(TAG, "Connecting to peer: ${device.deviceName}")

                    isConnecting = true
                    wifi.connect(device)

                } else {
                    Log.d(TAG, "No peers found")
                }

            } else {
                Log.d(TAG, "Already connecting, ignoring peers")
            }
        }
    }

    fun setPendingFile(uri: Uri) {
        pendingFileUri = uri
    }

    fun startAdvertising() {
        bleAdvertiser.startHelloLoop()
    }

    fun startFileTransfer(destNodeId: Int) {

        targetNodeId = destNodeId

        Log.d(TAG, "Starting transfer to node: $destNodeId")

        val packet = MeshPacket(
            version = BLEConstants.PROTOCOL_VERSION,
            type = BLEConstants.PACKET_TYPE_FILE_REQUEST,
            packetId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            srcNodeId = selfNodeId,
            destNodeId = destNodeId,
            ttl = BLEConstants.DEFAULT_TTL,
            payload = ByteArray(0)
        )

        repeat(5) {

            handler.postDelayed({

                bleAdvertiser.sendRawPacket(packet)

            }, it * 600L)
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

        scanCallback = object : ScanCallback() {

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

                        if (packet.destNodeId == selfNodeId) {

                            Log.d(TAG, "Received FILE_REQUEST → becoming GO")

                            val reply = MeshPacket(
                                version = BLEConstants.PROTOCOL_VERSION,
                                type = BLEConstants.PACKET_TYPE_ROUTE_REPLY,
                                packetId = packet.packetId,
                                srcNodeId = selfNodeId,
                                destNodeId = packet.srcNodeId,
                                ttl = BLEConstants.DEFAULT_TTL,
                                payload = ByteArray(0)
                            )

                            bleAdvertiser.sendRawPacket(reply)

                            wifi.createGroup()
                        }
                    }

                    BLEConstants.PACKET_TYPE_ROUTE_REPLY -> {

                        if (packet.destNodeId == selfNodeId) {

                            Log.d(TAG, "Received ROUTE_REPLY → starting WiFi discovery")

                            handler.postDelayed({
                                wifi.discoverPeers()
                            }, 1500)
                        }
                    }
                }
            }
        }

        scanner?.startScan(listOf(filter), settings, scanCallback)

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