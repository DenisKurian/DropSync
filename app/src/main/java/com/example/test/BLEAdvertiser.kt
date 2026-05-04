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

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val advertiser: BluetoothLeAdvertiser? =
        adapter?.bluetoothLeAdvertiser

    private val handler = Handler(Looper.getMainLooper())

    private var callback: AdvertiseCallback? = null

    private var helloRunning = false
    private var helloPaused = false
    private var advertisingBusy = false

    private val packetCounter = AtomicInteger(0)

    private val selfNodeId =
        MeshIdentity.getNodeId(context)

    fun isSupported(): Boolean {
        return adapter != null &&
                adapter.isEnabled &&
                adapter.isMultipleAdvertisementSupported &&
                advertiser != null
    }

    /* ================= HELLO LOOP ================= */

    fun startHelloLoop() {

        if (!isSupported()) {
            Log.e(TAG, "BLE advertising not supported")
            return
        }

        if (helloRunning) return

        helloRunning = true
        helloPaused = false

        scheduleHello()
    }

    fun stopHelloLoop() {

        helloRunning = false
        helloPaused = true

        handler.removeCallbacksAndMessages(null)

        stopAdvertising()
    }

    private fun scheduleHello() {

        if (!helloRunning || helloPaused) return

        handler.postDelayed({
            sendHello()
        }, HELLO_INTERVAL)
    }

    private var wifiDirectDeviceName: String? = null

    fun setDeviceName(name: String) {
        wifiDirectDeviceName = name
    }

    private fun sendHello() {

        if (!helloRunning || helloPaused) return

        val deviceName =
            (wifiDirectDeviceName ?: android.os.Build.MODEL ?: "Android").take(15)

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

        if (advertisingBusy) {
            Log.d(TAG, "Advertiser busy, skipping packet")
            return
        }

        advertisingBusy = true

        try {
            callback?.let {
                advertiser?.stopAdvertising(it)
            }
        } catch (_: Exception) {}

        callback = null

        val dataBytes = packet.toBytes()

        if (dataBytes.size > 54 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && adapter?.isLeExtendedAdvertisingSupported == true) {
            val parameters = AdvertisingSetParameters.Builder()
                .setLegacyMode(false)
                .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
                .setConnectable(false)
                .build()

            val extData = AdvertiseData.Builder()
                .addManufacturerData(BLEConstants.MANUFACTURER_ID, dataBytes)
                .build()

            val extCallback = object : AdvertisingSetCallback() {
                override fun onAdvertisingSetStarted(
                    advertisingSet: AdvertisingSet?,
                    txPower: Int,
                    status: Int
                ) {
                    Log.d(TAG, "Extended Packet advertised → type=${packet.type} id=${packet.packetId} status=$status")
                }
                override fun onAdvertisingSetStopped(advertisingSet: AdvertisingSet?) {
                    Log.d(TAG, "Extended Packet stopped → id=${packet.packetId}")
                }
            }

            try {
                advertiser?.startAdvertisingSet(parameters, extData, null, null, null, extCallback)
                
                handler.postDelayed({
                    try {
                        advertiser?.stopAdvertisingSet(extCallback)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to stop extended adv", e)
                    }
                    advertisingBusy = false
                }, ADVERTISE_DURATION)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start extended advertising", e)
                // Fallback to legacy if this throws for some reason
            }
        }

        // Fallback to Legacy Advertising with dual-chunk splitting
        val chunk1 = dataBytes.take(27).toByteArray()
        val chunk2 = dataBytes.drop(27).take(27).toByteArray()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()

        val dataBuilder = AdvertiseData.Builder()
            .addManufacturerData(BLEConstants.MANUFACTURER_ID, chunk1)

        val scanBuilder = AdvertiseData.Builder()
        if (chunk2.isNotEmpty()) {
            scanBuilder.addManufacturerData(BLEConstants.MANUFACTURER_ID + 1, chunk2)
        }

        callback = object : AdvertiseCallback() {

            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {

                Log.d(TAG, "Packet advertised → type=${packet.type} id=${packet.packetId} (chunks=${if(chunk2.isEmpty()) 1 else 2})")
            }

            override fun onStartFailure(errorCode: Int) {

                Log.e(TAG, "Advertise failed: $errorCode")

                advertisingBusy = false
            }
        }

        if (chunk2.isNotEmpty()) {
            advertiser?.startAdvertising(settings, dataBuilder.build(), scanBuilder.build(), callback)
        } else {
            advertiser?.startAdvertising(settings, dataBuilder.build(), callback)
        }

        handler.postDelayed({

            stopAdvertising()

        }, ADVERTISE_DURATION)
    }

    /* ================= SEND DIRECT DATA ================= */

    fun sendDataToNode(
        payloadBytes: ByteArray,
        destNodeId: Int
    ): MeshPacket? {

        if (!isSupported()) {
            Log.e(TAG, "BLE advertising not supported")
            return null
        }

        pauseHello()

        val payloadBytesFinal =
            payloadBytes.take(BLEConstants.MAX_PAYLOAD_SIZE).toByteArray()

        val packet = MeshPacket(
            version = BLEConstants.PROTOCOL_VERSION,
            type = BLEConstants.PACKET_TYPE_DATA,
            packetId = packetCounter.incrementAndGet(),
            srcNodeId = selfNodeId,
            destNodeId = destNodeId,
            ttl = BLEConstants.DEFAULT_TTL,
            payload = payloadBytesFinal
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

    fun sendBroadcast(payloadBytes: ByteArray): MeshPacket? {

        return sendDataToNode(
            payloadBytes,
            BLEConstants.BROADCAST_NODE_ID
        )
    }

    /* ================= STOP ADVERTISING ================= */

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

