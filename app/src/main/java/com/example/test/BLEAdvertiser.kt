package com.example.test

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

class BLEAdvertiser(private val context: Context) {

    companion object {
        private const val TAG = "BLEAdvertiser"
        private const val HELLO_INTERVAL = 5_000L
    }

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val advertiser: BluetoothLeAdvertiser? =
        adapter?.bluetoothLeAdvertiser

    private val handler = Handler(Looper.getMainLooper())

    private var callback: AdvertiseCallback? = null
    private var helloRunning = false

    // Packet ID generator (thread-safe)
    private val packetCounter = AtomicInteger(0)

    fun isSupported(): Boolean =
        adapter != null &&
                adapter.isEnabled &&
                adapter.isMultipleAdvertisementSupported &&
                advertiser != null

    /* ================= HELLO LOOP ================= */

    fun startHelloLoop() {
        if (!isSupported()) {
            Log.e(TAG, "BLE not supported")
            return
        }

        if (helloRunning) return

        helloRunning = true
        sendHello()
    }

    fun stopHelloLoop() {
        helloRunning = false
        stopAdvertising()
    }

    private fun sendHello() {

        if (!helloRunning) return

        val nodeId = MeshIdentity.getNodeId(context)

        val packet = MeshPacket(
            version = BLEConstants.PROTOCOL_VERSION,
            type = BLEConstants.PACKET_TYPE_HELLO,
            packetId = packetCounter.incrementAndGet(),
            srcNodeId = nodeId,
            destNodeId = BLEConstants.BROADCAST_NODE_ID,
            ttl = 1,
            payload = byteArrayOf()
        )

        sendRawPacket(packet)

        handler.postDelayed({
            sendHello()
        }, HELLO_INTERVAL)
    }

    /* ================= GENERIC PACKET SENDER ================= */

    fun sendRawPacket(packet: MeshPacket) {

        if (!isSupported()) return

        val dataBytes = packet.toBytes()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addManufacturerData(
                BLEConstants.MANUFACTURER_ID,
                dataBytes
            )
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d(
                    TAG,
                    "Packet advertised → type=${packet.type} id=${packet.packetId} ttl=${packet.ttl}"
                )
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertise failed: $errorCode")
            }
        }

        advertiser?.startAdvertising(settings, data, callback)

        handler.postDelayed({
            stopAdvertising()
        }, 800)
    }

    /* ================= DATA MESSAGE ================= */

    fun sendData(message: String) {

        if (!isSupported()) {
            Log.e(TAG, "BLE not supported")
            return
        }

        val nodeId = MeshIdentity.getNodeId(context)

        val payloadBytes = message.toByteArray(Charsets.UTF_8)

        val packet = MeshPacket(
            version = BLEConstants.PROTOCOL_VERSION,
            type = BLEConstants.PACKET_TYPE_DATA,
            packetId = packetCounter.incrementAndGet(),
            srcNodeId = nodeId,
            destNodeId = BLEConstants.BROADCAST_NODE_ID,
            ttl = 3, // allow multi-hop
            payload = payloadBytes
        )

        Log.d(TAG, "Sending DATA: $message")

        sendRawPacket(packet)
    }

    /* ================= STOP ================= */

    private fun stopAdvertising() {
        try {
            callback?.let { advertiser?.stopAdvertising(it) }
        } catch (e: Exception) {
            Log.w(TAG, "stopAdvertising error: ${e.message}")
        } finally {
            callback = null
        }
    }
}
