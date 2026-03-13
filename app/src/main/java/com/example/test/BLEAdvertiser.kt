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
        private const val HELLO_INTERVAL = 5000L
        private const val ADVERTISE_DURATION = 800L
    }

    private var helloPaused = false
    private var advertisingBusy = false

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val advertiser: BluetoothLeAdvertiser? =
        adapter?.bluetoothLeAdvertiser

    private val handler = Handler(Looper.getMainLooper())

    private var callback: AdvertiseCallback? = null
    private var helloRunning = false

    private val packetCounter = AtomicInteger(0)

    private val selfNodeId =
        MeshIdentity.getNodeId(context)

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
        helloPaused = false

        scheduleHello()
    }

    fun stopHelloLoop() {
        helloRunning = false
        handler.removeCallbacksAndMessages(null)
        stopAdvertising()
    }

    private fun scheduleHello() {

        if (!helloRunning || helloPaused) return

        handler.postDelayed({
            sendHello()
        }, HELLO_INTERVAL)
    }

    private fun sendHello() {

        if (!helloRunning || helloPaused) return

        val deviceName =
            (android.os.Build.MODEL ?: "Android").take(10)

        val payload =
            deviceName.toByteArray(Charsets.UTF_8)

        val packet = MeshPacket(
            version = BLEConstants.PROTOCOL_VERSION,
            type = BLEConstants.PACKET_TYPE_HELLO,
            packetId = packetCounter.incrementAndGet(),
            srcNodeId = selfNodeId,
            destNodeId = BLEConstants.BROADCAST_NODE_ID,
            ttl = 1,
            payload = payload
        )

        sendRawPacket(packet)

        scheduleHello()
    }

    /* ================= HELLO CONTROL ================= */

    fun pauseHello() {
        helloPaused = true
        stopAdvertising()
    }

    fun resumeHello() {

        helloPaused = false

        if (helloRunning) {
            scheduleHello()
        }
    }

    /* ================= GENERIC PACKET SENDER ================= */

    fun sendRawPacket(packet: MeshPacket) {

        if (!isSupported()) return
        if (advertisingBusy) return

        advertisingBusy = true

        try {
            callback?.let {
                advertiser?.stopAdvertising(it)
            }
        } catch (_: Exception) {}

        callback = null

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
                Log.d(TAG, "Packet advertised → type=${packet.type} id=${packet.packetId}")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertise failed: $errorCode")
                advertisingBusy = false
            }
        }

        advertiser?.startAdvertising(settings, data, callback)

        handler.postDelayed({

            stopAdvertising()

        }, ADVERTISE_DURATION)
    }

    /* ================= SEND TO NODE ================= */

    fun sendDataToNode(
        message: String,
        destNodeId: Int
    ): MeshPacket? {

        if (!isSupported()) {
            Log.e(TAG, "BLE not supported")
            return null
        }

        pauseHello()

        val payloadBytes =
            message.take(10).toByteArray(Charsets.UTF_8)

        val packet = MeshPacket(
            version = BLEConstants.PROTOCOL_VERSION,
            type = BLEConstants.PACKET_TYPE_DATA,
            packetId = packetCounter.incrementAndGet(),
            srcNodeId = selfNodeId,
            destNodeId = destNodeId,
            ttl = BLEConstants.DEFAULT_TTL,
            payload = payloadBytes
        )

        handler.postDelayed({

            sendRawPacket(packet)

            Log.d(TAG, "Sending DATA to node=$destNodeId")

            handler.postDelayed({
                resumeHello()
            }, 2000)

        }, 600)

        return packet
    }

    fun sendBroadcast(message: String): MeshPacket? {

        return sendDataToNode(
            message,
            BLEConstants.BROADCAST_NODE_ID
        )
    }

    private fun stopAdvertising() {

        try {
            callback?.let {
                advertiser?.stopAdvertising(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopAdvertising error: ${e.message}")
        } finally {
            callback = null
            advertisingBusy = false
        }
    }
}