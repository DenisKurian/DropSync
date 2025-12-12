package com.example.test

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BLEViewModel(app: Application) : AndroidViewModel(app) {

    private val btManager = app.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = btManager?.adapter

    // scanner is mutable — avoid smart-cast issues by copying into local vals when used
    private var scanner: BluetoothLeScanner? = adapter?.bluetoothLeScanner

    // scanned devices
    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices
    private val deviceMap = mutableMapOf<String, ScannedDevice>()

    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())

    // Diagnostics state
    private val _showDiagnostics = MutableStateFlow(true)
    val showDiagnostics: StateFlow<Boolean> = _showDiagnostics

    private val _isEmulator = MutableStateFlow(false)
    val isEmulator: StateFlow<Boolean> = _isEmulator

    private val _advertisingSupported = MutableStateFlow(false)
    val advertisingSupported: StateFlow<Boolean> = _advertisingSupported

    private val _advertiserAvailable = MutableStateFlow(false)
    val advertiserAvailable: StateFlow<Boolean> = _advertiserAvailable

    private val _scannerAvailable = MutableStateFlow(false)
    val scannerAvailable: StateFlow<Boolean> = _scannerAvailable

    private val _statusMessage = MutableStateFlow("idle")
    val statusMessage: StateFlow<String> = _statusMessage

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    init {
        probeSupport() // initial probe
    }

    private fun addLog(line: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val new = listOf("[$ts] $line") + _logs.value
        _logs.value = new.take(50) // keep recent 50
        Log.d("BLEViewModel", line)
    }

    fun setShowDiagnostics(v: Boolean) { _showDiagnostics.value = v }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * probeSupport() populates the diagnostic flows so you can see what's available.
     */
    @MainThread
    fun probeSupport() {
        val isEmu = Build.FINGERPRINT.contains("generic") ||
                Build.FINGERPRINT.contains("unknown") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.PRODUCT.contains("sdk") ||
                Build.MANUFACTURER.contains("Genymotion")
        _isEmulator.value = isEmu

        val advSupported = adapter?.isMultipleAdvertisementSupported == true
        _advertisingSupported.value = advSupported

        val advAvailable = adapter?.bluetoothLeAdvertiser != null
        _advertiserAvailable.value = advAvailable

        val scanAvail = adapter?.bluetoothLeScanner != null
        _scannerAvailable.value = scanAvail

        _statusMessage.value = if (isBluetoothEnabled()) "Bluetooth enabled" else "Bluetooth disabled"
        _lastError.value = null

        addLog("probeSupport: emulator=$isEmu advSupported=$advSupported advAvail=$advAvailable scannerAvail=$scanAvail")
    }

    fun startScan(stopAfterMillis: Long = 10_000L) {
        if (scanning) {
            addLog("startScan: already scanning")
            return
        }
        if (adapter == null || !adapter.isEnabled) {
            addLog("startScan: adapter null or disabled")
            _lastError.value = "Bluetooth adapter missing or disabled"
            return
        }

        // get safe local reference
        val localScanner = adapter.bluetoothLeScanner
        if (localScanner == null) {
            addLog("startScan: bluetoothLeScanner is null")
            _scannerAvailable.value = false
            _lastError.value = "scanner unavailable"
            return
        }

        deviceMap.clear()
        _devices.value = emptyList()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device?.address ?: return
                val name = result.device?.name ?: result.scanRecord?.deviceName ?: "Unknown"
                val rssi = result.rssi
                val dev = ScannedDevice(address, name, rssi)
                deviceMap[address] = dev
                _devices.value = deviceMap.values.toList()
                addLog("onScanResult: $name / $address / $rssi")
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                addLog("onBatchScanResults: ${results.size}")
            }

            override fun onScanFailed(errorCode: Int) {
                addLog("onScanFailed: code=$errorCode")
                _lastError.value = "scanFailed: $errorCode"
            }
        }

        scanning = true
        _statusMessage.value = "scanning"
        _lastError.value = null
        _scannerAvailable.value = true
        addLog("startScan: mode=LOW_LATENCY")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        localScanner.startScan(null, settings, scanCallback)

        handler.postDelayed({
            // use local reference of scanner again
            val s = adapter.bluetoothLeScanner
            try {
                s?.stopScan(scanCallback)
                addLog("auto-stop scan after $stopAfterMillis ms")
            } catch (ex: Exception) {
                addLog("stopScan exception: ${ex.message}")
            }
            scanning = false
            _statusMessage.value = "idle"
        }, stopAfterMillis)
    }

    fun stopScan(callback: ScanCallback? = null) {
        if (!scanning) {
            addLog("stopScan: not scanning")
            return
        }
        val s = scanner ?: adapter?.bluetoothLeScanner
        try {
            s?.stopScan(callback)
            addLog("stopScan called")
        } catch (ex: Exception) {
            addLog("stopScan exception: ${ex.message}")
            _lastError.value = ex.message
        } finally {
            scanning = false
            _statusMessage.value = "idle"
        }
    }
}
