package com.example.test

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.util.*

class BLEAdvertiser(private val adapter: BluetoothAdapter?) {

    private var advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser
    private var advertising = false
    private var callback: AdvertiseCallback? = null

    companion object {
        private const val TAG = "BLEAdvertiser"
        // optional small service UUID — comment out for pure name-only advertising
        // val SERVICE_UUID = ParcelUuid.fromString("0000FEED-0000-1000-8000-00805F9B34FB")
    }

    fun isSupported(): Boolean {
        if (adapter == null) return false
        return adapter.isEnabled && adapter.isMultipleAdvertisementSupported && advertiser != null
    }

    fun isAdvertising(): Boolean = advertising

    fun startAdvertising(
        context: Context? = null,
        onStarted: () -> Unit = {},
        onFailed: (Int, String) -> Unit = { code, message -> Log.w(TAG, "Advertise failed $code: $message") }
    ) {
        if (adapter == null) {
            onFailed(-1, "Bluetooth adapter is null")
            return
        }
        if (!adapter.isEnabled) {
            onFailed(-2, "Bluetooth disabled")
            return
        }
        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            onFailed(-3, "Device doesn't support BLE advertising (advertiser == null)")
            return
        }

        // Build settings (balanced / low latency for quick discovery)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true) // set as needed
            .build()

        // Minimal data: include device name only (safe size)
        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
        // If you want to include a small service UUID uncomment below (increase payload size)
        // dataBuilder.addServiceUuid(SERVICE_UUID)

        val advertiseData = dataBuilder.build()

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                advertising = true
                Log.i(TAG, "Advertising started: $settingsInEffect")
                onStarted()
            }

            override fun onStartFailure(errorCode: Int) {
                advertising = false
                val msg = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE (payload > 31 bytes)"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED (peripheral mode unsupported)"
                    else -> "UNKNOWN_ERROR($errorCode)"
                }
                Log.w(TAG, "Advertise failed: $errorCode -> $msg")
                onFailed(errorCode, msg)
            }
        }

        try {
            advertiser?.startAdvertising(settings, advertiseData, callback)
        } catch (t: Throwable) {
            advertising = false
            Log.e(TAG, "startAdvertising threw", t)
            onFailed(-999, "exception: ${t.localizedMessage}")
        }
    }

    fun stopAdvertising() {
        try {
            callback?.let {
                advertiser?.stopAdvertising(it)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "stopAdvertising threw: ${t.message}")
        } finally {
            advertising = false
            callback = null
        }
    }
}
