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
import java.util.concurrent.ConcurrentHashMap

class BLEViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "BLEViewModel"
    }

    /* ---------------------------------- */
    /* Mesh Components                    */
    /* ---------------------------------- */

    private val neighborTable = NeighborTable()
    private val bleAdvertiser = BLEAdvertiser(app)

    private val selfNodeId = MeshIdentity.getNodeId(app)

    private val seenPackets = ConcurrentHashMap<String, Long>()

    /* ---------------------------------- */
    /* Mesh Visualization Data            */
    /* ---------------------------------- */

    private val _meshEvents = MutableStateFlow<List<MeshEvent>>(emptyList())
    val meshEvents: StateFlow<List<MeshEvent>> = _meshEvents

    private val _meshNodes = MutableStateFlow<List<MeshNode>>(emptyList())
    val meshNodes: StateFlow<List<MeshNode>> = _meshNodes

    private val _meshEdges = MutableStateFlow<List<MeshEdge>>(emptyList())
    val meshEdges: StateFlow<List<MeshEdge>> = _meshEdges

    /* -------- Packet Animation -------- */

    private val _packetAnimations =
        MutableStateFlow<List<PacketAnimation>>(emptyList())

    val packetAnimations: StateFlow<List<PacketAnimation>> =
        _packetAnimations

    private fun addPacketAnimation(from: Int, to: Int) {

        val list = _packetAnimations.value.toMutableList()

        list.add(PacketAnimation(from, to))

        _packetAnimations.value = list.takeLast(20)
    }

    /* ---------------------------------- */
    /* Helpers                            */
    /* ---------------------------------- */

    private fun addMeshEvent(event: MeshEvent) {
        val current = _meshEvents.value.toMutableList()
        current.add(0, event)
        _meshEvents.value = current.take(50)
    }

    private fun updateGraph(packet: MeshPacket) {

        val nodes = _meshNodes.value.toMutableList()
        val edges = _meshEdges.value.toMutableList()

        if (nodes.none { it.nodeId == packet.srcNodeId }) {
            nodes.add(MeshNode(packet.srcNodeId))
        }

        if (nodes.none { it.nodeId == selfNodeId }) {
            nodes.add(MeshNode(selfNodeId))
        }

        edges.add(
            MeshEdge(
                from = packet.srcNodeId,
                to = selfNodeId
            )
        )

        _meshNodes.value = nodes.takeLast(20)
        _meshEdges.value = edges.takeLast(40)
    }

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

    fun sendMessage(text: String) {
        bleAdvertiser.sendData(text)
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun startScan(stopAfterMillis: Long = 15_000L) {

        if (scanning) return
        if (adapter == null || !adapter.isEnabled) return

        _devices.value = emptyList()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {

            override fun onScanResult(callbackType: Int, result: ScanResult) {

                val record = result.scanRecord ?: return
                val data =
                    record.manufacturerSpecificData[BLEConstants.MANUFACTURER_ID]
                        ?: return

                val packet = MeshPacket.fromBytes(data) ?: return

                if (packet.version != BLEConstants.PROTOCOL_VERSION) return
                if (packet.srcNodeId == selfNodeId) return

                val uniqueKey = "${packet.srcNodeId}_${packet.packetId}"

                if (seenPackets.containsKey(uniqueKey)) return
                seenPackets[uniqueKey] = System.currentTimeMillis()

                val address = result.device.address ?: return
                val rssi = result.rssi

                when (packet.type) {

                    /* -------- HELLO -------- */

                    BLEConstants.PACKET_TYPE_HELLO -> {

                        neighborTable.update(
                            nodeId = packet.srcNodeId,
                            address = address,
                            rssi = rssi
                        )

                        updateUi()

                        Log.d(TAG, "HELLO from ${packet.srcNodeId.toString(16)}")
                    }

                    /* -------- DATA -------- */

                    BLEConstants.PACKET_TYPE_DATA -> {

                        val message =
                            packet.payload.toString(Charsets.UTF_8)

                        Log.d(
                            TAG,
                            "DATA from ${packet.srcNodeId.toString(16)} → $message TTL=${packet.ttl}"
                        )

                        addMeshEvent(
                            MeshEvent(
                                packetId = packet.packetId,
                                srcNodeId = packet.srcNodeId,
                                message = message,
                                ttl = packet.ttl.toInt()
                            )
                        )

                        updateGraph(packet)

                        // 🔥 animation: source → this node
                        addPacketAnimation(packet.srcNodeId, selfNodeId)

                        // Deliver locally
                        if (packet.destNodeId == selfNodeId ||
                            packet.destNodeId == BLEConstants.BROADCAST_NODE_ID
                        ) {
                            sendAck(packet)
                        }

                        // Forward
                        if (packet.ttl > 1) {

                            val forwarded = packet.copy(
                                ttl = (packet.ttl - 1).toByte()
                            )

                            bleAdvertiser.sendRawPacket(forwarded)

                            // 🔥 animation: forward hop
                            addPacketAnimation(selfNodeId, forwarded.destNodeId)

                            Log.d(TAG, "Forwarded packet id=${forwarded.packetId}")
                        }
                    }

                    /* -------- ACK -------- */

                    BLEConstants.PACKET_TYPE_ACK -> {

                        val ackedId =
                            packet.payload.toString(Charsets.UTF_8)

                        Log.d(TAG, "ACK received for packet $ackedId")
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
            }
        }

        scanner?.startScan(null, settings, scanCallback)
        scanning = true

        handler.postDelayed({ stopScan() }, stopAfterMillis)
    }

    /* ---------------------------------- */
    /* ACK SYSTEM                         */
    /* ---------------------------------- */

    private fun sendAck(original: MeshPacket) {

        val ackPayload =
            original.packetId.toString().toByteArray()

        val ackPacket = MeshPacket(
            version = BLEConstants.PROTOCOL_VERSION,
            type = BLEConstants.PACKET_TYPE_ACK,
            packetId = original.packetId,
            srcNodeId = selfNodeId,
            destNodeId = original.srcNodeId,
            ttl = BLEConstants.DEFAULT_TTL,
            payload = ackPayload
        )

        bleAdvertiser.sendRawPacket(ackPacket)

        Log.d(TAG, "ACK sent for packet ${original.packetId}")
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

        scanCallback?.let { scanner?.stopScan(it) }

        scanning = false
        scanCallback = null
    }
}
