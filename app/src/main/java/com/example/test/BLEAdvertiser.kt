package com.example.test

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

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
            srcNodeId = nodeId,
            destNodeId = BLEConstants.BROADCAST_NODE_ID,
            ttl = 1,
            payload = ByteArray(0)
        )

        val payload = packet.toBytes()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addManufacturerData(
                BLEConstants.MANUFACTURER_ID,
                payload
            )
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d(TAG, "HELLO advertised")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "HELLO advertise failed: $errorCode")
            }
        }

        advertiser?.startAdvertising(settings, data, callback)

        handler.postDelayed({
            stopAdvertising()
        }, 800)

        handler.postDelayed({
            sendHello()
        }, HELLO_INTERVAL)
    }

    /* ================= DATA SEND ================= */
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

        callback = object : AdvertiseCallback() {}

        advertiser?.startAdvertising(settings, data, callback)

        handler.postDelayed({
            stopAdvertising()
        }, 800)
    }

    fun sendDataMessage(message: String) {

        if (!isSupported()) {
            Log.e(TAG, "BLE not supported")
            return
        }

        val nodeId = MeshIdentity.getNodeId(context)
        val payloadBytes = message.toByteArray()

        val packet = MeshPacket(
            version = BLEConstants.PROTOCOL_VERSION,
            type = BLEConstants.PACKET_TYPE_DATA,
            srcNodeId = nodeId,
            destNodeId = BLEConstants.BROADCAST_NODE_ID,
            ttl = 3,
            payload = payloadBytes
        )

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
                Log.d(TAG, "DATA message sent: $message")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "DATA advertise failed: $errorCode")
            }
        }

        advertiser?.startAdvertising(settings, data, callback)

        handler.postDelayed({
            stopAdvertising()
        }, 800)
    }

    private fun stopAdvertising() {
        try {
            callback?.let { advertiser?.stopAdvertising(it) }
        } catch (e: Exception) {
            Log.w(TAG, "stopAdvertising error: ${e.message}")
        } finally {
            callback = null
        }
    }
    fun sendData(message: String) {

        if (!isSupported()) {
            Log.e(TAG, "BLE not supported")
            return
        }

        val nodeId = MeshIdentity.getNodeId(context)

        val packet = MeshPacket(
            version = BLEConstants.PROTOCOL_VERSION,
            type = BLEConstants.PACKET_TYPE_DATA,
            srcNodeId = nodeId,
            destNodeId = BLEConstants.BROADCAST_NODE_ID,
            ttl = 3, // allow future forwarding
            payload = message.toByteArray(Charsets.UTF_8)
        )

        val payload = packet.toBytes()

        Log.d(TAG, "Sending DATA: $message")

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addManufacturerData(BLEConstants.MANUFACTURER_ID, payload)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.d(TAG, "DATA advertised")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "DATA advertise failed: $errorCode")
            }
        }

        advertiser?.startAdvertising(settings, data, callback)

        handler.postDelayed({
            advertiser?.stopAdvertising(callback)
        }, 800)
    }

}
