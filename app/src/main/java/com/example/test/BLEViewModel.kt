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


    companion object { private const val TAG = "BLEViewModel" }

    private val neighborTable = NeighborTable()
    private val bleAdvertiser = BLEAdvertiser(application)
    private val selfNodeId = MeshIdentity.getNodeId(application)
    private val wifiManager = WifiDirectManager(application)
    private val seenPackets = ConcurrentHashMap<String, Long>()
    private val _receivedFileUri = MutableStateFlow<String?>(null)

    val receivedFileUri: StateFlow<String?> = _receivedFileUri
    private var pendingFileUri: Uri? = null
    private var wifiTransferStarted = false
    init {

        wifiManager.onConnected = { host ->
            Log.d(TAG, "WiFi connected → preparing file send")
            startWifiSendThread(host)
        }

        wifiManager.onGroupOwner = {
            Log.d(TAG, "Device became GO → waiting for incoming file")
        }

        FileServer.onFileReceived = { uri ->

            Log.d(TAG, "File received in ViewModel")

            _receivedFileUri.value = uri
        }
    }
    fun setPendingFile(uri: Uri) { pendingFileUri = uri }

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices

    private val btManager = application.getSystemService(BluetoothManager::class.java)
    private val adapter = btManager?.adapter
    private val scanner = adapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())

    private var scanning = false
    private var scanCallback: ScanCallback? = null

    init {

        // When device becomes Wi-Fi Direct client (sender)
        wifiManager.onConnected = { host ->
            Log.d(TAG, "WiFi connected → preparing file send")
            startWifiSendThread(host)
        }

        // When device becomes Group Owner (receiver)
        wifiManager.onGroupOwner = {
            Log.d(TAG, "Device became GO → waiting for incoming file")
            // DO NOTHING. FileServer is already running.
        }
    }

    /* ================= ADVERTISE ================= */

    fun startAdvertising() {
        bleAdvertiser.startHelloLoop()
    }

    /* ================= FILE TRANSFER ================= */

    fun startFileTransfer(destNodeId: Int) {

        wifiTransferStarted = false

        val requestId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val packet = MeshPacket(
            version = BLEConstants.PROTOCOL_VERSION,
            type = BLEConstants.PACKET_TYPE_FILE_REQUEST,
            packetId = requestId,
            srcNodeId = selfNodeId,
            destNodeId = destNodeId,
            ttl = BLEConstants.DEFAULT_TTL,
            payload = ByteArray(0)
        )

        Log.d(TAG, "Starting route discovery to $destNodeId")

        bleAdvertiser.pauseHello()

        handler.postDelayed({

            repeat(6) { i ->

                handler.postDelayed({
                    bleAdvertiser.sendRawPacket(packet)
                    Log.d(TAG, "FILE_REQUEST advertised")
                }, i * 700L)

            }

            handler.postDelayed({
                bleAdvertiser.resumeHello()
            }, 5000)

        }, 1000)
    }

    /* ================= WIFI SEND ================= */

    private fun startWifiSendThread(host: String) {

        if (wifiTransferStarted) return
        wifiTransferStarted = true

        val uri = pendingFileUri

        if (uri == null) {
            Log.e(TAG, "pendingFileUri is NULL → cannot send file")
            wifiTransferStarted = false
            return
        }

        stopScan()
        bleAdvertiser.pauseHello()

        Thread {

            try {

                Thread.sleep(3000) // allow Wi-Fi network to stabilize

                Log.d(TAG, "Starting file transfer to $host")

                val client = FileClient(getApplication())
                client.sendFile(uri, host)

                Log.d(TAG, "FileClient finished sending")

                pendingFileUri = null

            } catch (e: Exception) {

                Log.e(TAG, "File send error", e)

            } finally {

                try {
                    wifiManager.disconnect()
                } catch (e: Exception) {
                    Log.e(TAG, "WiFi disconnect error", e)
                }

                handler.post {

                    startScan()
                    bleAdvertiser.resumeHello()

                    wifiTransferStarted = false
                }
            }

        }.start()
    }

    /* ================= BLE SCANNING ================= */

    fun startScan() {

        if (scanning) return

        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter not ready")
            return
        }

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

                val key = "${packet.srcNodeId}_${packet.packetId}_${packet.type}"

                if (seenPackets.containsKey(key)) return

                seenPackets[key] = System.currentTimeMillis()

                when (packet.type) {

                    BLEConstants.PACKET_TYPE_HELLO -> {

                        val deviceName = packet.payload.toString(Charsets.UTF_8)

                        neighborTable.update(
                            nodeId = packet.srcNodeId,
                            address = result.device.address ?: "",
                            rssi = result.rssi,
                            name = deviceName
                        )

                        updateUi()

                        Log.d(TAG, "HELLO from $deviceName")
                    }

                    BLEConstants.PACKET_TYPE_FILE_REQUEST -> {

                        Log.d(TAG, "FILE_REQUEST received")

                        if (packet.destNodeId == selfNodeId) {

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

                            Log.d(TAG, "Sent ROUTE_REPLY")
                            Log.d(TAG, "Receiver preparing WiFi Direct")

                            wifiManager.createGroup()
                        }
                    }

                    BLEConstants.PACKET_TYPE_ROUTE_REPLY -> {

                        Log.d(TAG, "ROUTE_REPLY received")

                        if (packet.destNodeId == selfNodeId) {

                            Log.d(TAG, "Route established → starting WiFi connection")

                            wifiManager.discoverAndConnect()
                        }
                    }
                }
            }
        }

        scanner?.startScan(listOf(filter), settings, scanCallback)

        scanning = true

        Log.d(TAG, "BLE scanning started")
    }

    fun stopScan() {

        if (!scanning) return

        scanCallback?.let { scanner?.stopScan(it) }

        scanning = false
        scanCallback = null

        Log.d(TAG, "BLE scanning stopped")
    }

    /* ================= UI UPDATE ================= */

    private fun updateUi() {

        val list = neighborTable.getAll().map {

            ScannedDevice(
                nodeId = it.nodeId,
                address = it.address,
                name = it.name,
                rssi = it.rssi
            )
        }

        _devices.value = list
    }

}
