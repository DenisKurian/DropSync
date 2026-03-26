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

class BLEViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BLEViewModel"
    }

    private enum class TransferState {
        IDLE,
        WAITING_FOR_REPLY,
        CONNECTING_WIFI,
        SENDING,
        RECEIVING
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
    private var groupCreated = false

    private val btManager = application.getSystemService(BluetoothManager::class.java)
    private val adapter = btManager?.adapter
    private val scanner = adapter?.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())

    private var scanning = false

    init {

        wifi.onConnected = { host ->
            if (state == TransferState.CONNECTING_WIFI || state == TransferState.SENDING) {
                Log.d(TAG, "WiFi connected → sending file to $host")
                state = TransferState.SENDING

                pendingFileUri?.let { uri ->
                    Thread {
                        val success = FileClient(getApplication()).sendFile(uri, host)
                        Log.d(TAG, "File send result: $success")

                        wifi.disconnect()
                        resetTransferState()
                    }.start()
                } ?: run {
                    Log.e(TAG, "No pending file URI set")
                    wifi.disconnect()
                    resetTransferState()
                }
            } else {
                Log.d(TAG, "Ignoring onConnected in state=$state")
            }
        }

        wifi.onGroupOwner = {
            Log.d(TAG, "Device became GO → waiting for file")
            state = TransferState.RECEIVING
        }

        FileServer.onFileReceived = { uri ->
            Log.d(TAG, "File received → $uri")
            _receivedFileUri.value = uri

            wifi.disconnect()
            resetTransferState()
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

    private fun resetTransferState() {
        state = TransferState.IDLE
        isConnecting = false
        groupCreated = false
        targetNodeId = null
        bleAdvertiser.resumeHello()
    }

    fun setPendingFile(uri: Uri) {
        pendingFileUri = uri
    }

    fun startAdvertising() {
        bleAdvertiser.startHelloLoop()
    }

    fun startFileTransfer(destNodeId: Int) {
        if (state != TransferState.IDLE) {
            Log.d(TAG, "Transfer already in progress")
            return
        }

        wifi.disconnect()
        bleAdvertiser.pauseHello()

        targetNodeId = destNodeId
        state = TransferState.WAITING_FOR_REPLY

        Log.d(TAG, "Starting transfer to node: $destNodeId")

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
                        if (packet.destNodeId == selfNodeId && state == TransferState.IDLE && !groupCreated) {
                            Log.d(TAG, "FILE_REQUEST → becoming GO")

                            groupCreated = true
                            state = TransferState.RECEIVING
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

                            wifi.createGroup()
                        }
                    }

                    BLEConstants.PACKET_TYPE_ROUTE_REPLY -> {
                        if (packet.destNodeId == selfNodeId && state == TransferState.WAITING_FOR_REPLY) {
                            Log.d(TAG, "ROUTE_REPLY → discovering peers")

                            state = TransferState.CONNECTING_WIFI
                            handler.postDelayed({
                                wifi.discoverPeers()
                            }, 1200)
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