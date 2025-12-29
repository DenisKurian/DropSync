package com.example.test

import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.bluetooth.le.ScanFilter


class BLEViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "BLEViewModel"
    }

    private val btManager = app.getSystemService(BluetoothManager::class.java)
    private val adapter = btManager?.adapter
    private val scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner

    private val handler = Handler(Looper.getMainLooper())

    private var scanning = false
    private var scanCallback: ScanCallback? = null

    private val deviceMap = mutableMapOf<String, ScannedDevice>()

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices

    /* ---------- Diagnostics ---------- */

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private fun log(msg: String) {
        Log.d(TAG, msg)
        _logs.value = (listOf("[${System.currentTimeMillis() % 100000}] $msg") + _logs.value)
            .take(10)
    }

    /* ---------- Public API ---------- */

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun startScan(stopAfterMillis: Long = 15000L) {
        if (scanning) {
            log("startScan: already scanning")
            return
        }

        if (adapter == null || !adapter.isEnabled) {
            log("Bluetooth disabled or adapter null")
            return
        }

        deviceMap.clear()
        _devices.value = emptyList()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BLEConstants.SERVICE_UUID))
            .build()
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BLEConstants.SERVICE_UUID))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {

            override fun onScanResult(type: Int, result: ScanResult) {
                val address = result.device.address ?: return
                val name = result.device.name ?: "BLE Device"
                val rssi = result.rssi

                deviceMap[address] = ScannedDevice(address, name, rssi)
                _devices.value = deviceMap.values.toList()

                log("FOUND $address RSSI=$rssi")
            }

            override fun onScanFailed(errorCode: Int) {
                log("Scan failed: error=$errorCode")
            }
        }

        try {
            scanner?.startScan(filters, settings, scanCallback)
            scanning = true
            log("Scan started (filtered by SERVICE_UUID)")
        } catch (e: SecurityException) {
            log("Scan failed: missing permission")
        }

        handler.postDelayed({
            stopScan()
        }, stopAfterMillis)
    }

    fun stopScan() {
        if (!scanning) return

        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (e: Exception) {
            log("stopScan error: ${e.message}")
        }

        scanning = false
        scanCallback = null
        log("Scan stopped")
    }
}
