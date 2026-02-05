package com.example.test

import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.HashSet

class BLEViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "BLEViewModel"
    }

    /* ---------------------------------- */
    /* Mesh Components                    */
    /* ---------------------------------- */

    private val neighborTable = NeighborTable()
    private val bleAdvertiser = BLEAdvertiser(app)

    // Prevent duplicate forwarding
    private val seenPackets = HashSet<String>()

    /* ---------------------------------- */
    /* BLE Core                           */
    /* ---------------------------------- */

    private val btManager = app.getSystemService(BluetoothManager::class.java)
    private val adapter = btManager?.adapter
    private val scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())

    private var scanning = false
    private var scanCallback: ScanCallback? = null

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices

    /* ---------------------------------- */
    /* Public API                         */
    /* ---------------------------------- */

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun startScan(stopAfterMillis: Long = 15_000L) {

        if (scanning) {
            Log.d(TAG, "Already scanning")
            return
        }

        if (adapter == null || !adapter.isEnabled) {
            Log.d(TAG, "Bluetooth disabled or adapter null")
            return
        }

        _devices.value = emptyList()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val selfNodeId = MeshIdentity.getNodeId(getApplication())

        scanCallback = object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult) {

                val record = result.scanRecord ?: return
                val data =
                    record.manufacturerSpecificData[BLEConstants.MANUFACTURER_ID]
                        ?: return

                val packet = MeshPacket.fromBytes(data) ?: return

                if (packet.version != BLEConstants.PROTOCOL_VERSION) return
                if (packet.srcNodeId == selfNodeId) return

                val packetId =
                    "${packet.srcNodeId}_${packet.payload.contentHashCode()}"

                // Duplicate suppression
                if (seenPackets.contains(packetId)) return
                seenPackets.add(packetId)

                val address = result.device.address ?: return
                val rssi = result.rssi

                when (packet.type) {

                    /* ---------------- HELLO ---------------- */

                    BLEConstants.PACKET_TYPE_HELLO -> {

                        neighborTable.update(
                            nodeId = packet.srcNodeId,
                            address = address,
                            rssi = rssi
                        )

                        updateUi()

                        Log.d(
                            TAG,
                            "HELLO from ${packet.srcNodeId.toString(16)}"
                        )
                    }

                    /* ---------------- DATA ---------------- */

                    BLEConstants.PACKET_TYPE_DATA -> {

                        val message =
                            packet.payload.toString(Charsets.UTF_8)

                        Log.d(
                            TAG,
                            "DATA from ${packet.srcNodeId.toString(16)} → $message (TTL=${packet.ttl})"
                        )

                        // If I am destination → consume
                        if (packet.destNodeId == selfNodeId ||
                            packet.destNodeId == BLEConstants.BROADCAST_NODE_ID
                        ) {
                            Log.d(TAG, "Message delivered to this node")
                        }

                        // Forward if TTL > 0
                        if (packet.ttl > 0) {

                            val newTtl = (packet.ttl - 1).toByte()

                            val forwarded = packet.copy(ttl = newTtl)


                            bleAdvertiser.sendRawPacket(forwarded)

                            Log.d(
                                TAG,
                                "Forwarded packet with TTL=${forwarded.ttl}"
                            )
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
            }
        }

        try {
            scanner?.startScan(null, settings, scanCallback)
            scanning = true
            Log.d(TAG, "Scan started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Scan failed: missing permission")
        }

        handler.postDelayed({ stopScan() }, stopAfterMillis)
    }

    /* ---------------------------------- */
    /* UI Update                          */
    /* ---------------------------------- */

    private fun updateUi() {
        val now = System.currentTimeMillis()

        _devices.value = neighborTable.getAll().map {
            val ageSec = (now - it.lastSeen) / 1000

            ScannedDevice(
                address = it.address,
                name = "NODE_${it.nodeId.toString(16)} • ${ageSec}s ago",
                rssi = it.rssi
            )
        }
    }

    /* ---------------------------------- */
    /* Stop Scan                          */
    /* ---------------------------------- */

    fun stopScan() {
        if (!scanning) return

        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (e: Exception) {
            Log.w(TAG, "stopScan error: ${e.message}")
        }

        scanning = false
        scanCallback = null
        Log.d(TAG, "Scan stopped")
    }
}
