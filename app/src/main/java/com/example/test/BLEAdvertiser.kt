package com.example.test

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import android.util.Log

class BLEAdvertiser(private val adapter: BluetoothAdapter?) {

    private var advertiser: BluetoothLeAdvertiser? = adapter?.bluetoothLeAdvertiser
    private var advertising = false
    private var callback: AdvertiseCallback? = null

    companion object {
        private const val TAG = "BLEAdvertiser"
    }

    fun isSupported(): Boolean {
        return adapter != null &&
                adapter.isEnabled &&
                adapter.isMultipleAdvertisementSupported &&
                advertiser != null
    }

    fun isAdvertising(): Boolean = advertising

    fun startAdvertising(
        onStarted: () -> Unit = {},
        onFailed: (Int, String) -> Unit = { code, msg ->
            Log.w(TAG, "Advertise failed $code: $msg")
        }
    ) {
        if (adapter == null) {
            onFailed(-1, "Bluetooth adapter is null")
            return
        }

        if (!adapter.isEnabled) {
            onFailed(-2, "Bluetooth is disabled")
            return
        }

        advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            onFailed(-3, "BLE advertising not supported")
            return
        }

        if (advertising) {
            onFailed(-4, "Already advertising")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        // 🔑 THIS IS THE MOST IMPORTANT PART
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(BLEConstants.SERVICE_UUID))
            .setIncludeDeviceName(true)
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                advertising = true
                Log.i(TAG, "Advertising started")
                onStarted()
            }

            override fun onStartFailure(errorCode: Int) {
                advertising = false
                val msg = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                    else -> "UNKNOWN_ERROR"
                }
                Log.w(TAG, "Advertise failed: $errorCode ($msg)")
                onFailed(errorCode, msg)
            }
        }

        try {
            advertiser?.startAdvertising(settings, data, callback)
        } catch (t: Throwable) {
            advertising = false
            onFailed(-999, t.message ?: "Exception")
        }
    }

    fun stopAdvertising() {
        try {
            callback?.let { advertiser?.stopAdvertising(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "stopAdvertising error: ${t.message}")
        } finally {
            advertising = false
            callback = null
        }
    }
}
