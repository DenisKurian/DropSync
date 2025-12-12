package com.example.test

import android.Manifest
import android.bluetooth.BluetoothManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult

class MainActivity : ComponentActivity() {

    private lateinit var bleAdvertiser: BLEAdvertiser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val btManager = getSystemService(BluetoothManager::class.java)
        val adapter = btManager?.adapter
        bleAdvertiser = BLEAdvertiser(adapter)

        setContent {
            MaterialTheme {
                Surface {
                    DeviceDiscoveryScreen(advertiser = bleAdvertiser)
                }
            }
        }
    }
}

@Composable
fun DeviceDiscoveryScreen(
    advertiser: BLEAdvertiser,
    bleViewModel: BLEViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val context = LocalContext.current
    val devices by bleViewModel.devices.collectAsState()

    val permissions = remember {
        mutableStateListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val allGranted = perms.values.all { it }
        if (!allGranted) {
            Toast.makeText(context, "Permissions required for BLE", Toast.LENGTH_SHORT).show()
        }
    }

    // ask once on open
    LaunchedEffect(Unit) {
        launcher.launch(permissions)
    }

    Scaffold(
        floatingActionButton = {
            Column {
                ExtendedFloatingActionButton(
                    text = { Text("Advertise") },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.mynauisendsolid),
                            contentDescription = "Advertise"
                        )
                    },
                    onClick = {
                        advertiser.startAdvertising(
                            onStarted = {
                                Toast.makeText(context, "Advertising started", Toast.LENGTH_SHORT).show()
                            },
                            onFailed = { code, message ->
                                Toast.makeText(context, "Advertise failed ($code): $message", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExtendedFloatingActionButton(
                    text = { Text("Scan") },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.mynauisendsolid),
                            contentDescription = "Scan"
                        )
                    },
                    onClick = {
                        if (!bleViewModel.isBluetoothEnabled()) {
                            Toast.makeText(context, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
                            return@ExtendedFloatingActionButton
                        }
                        launcher.launch(permissions)
                        bleViewModel.startScan(stopAfterMillis = 15_000L)
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(text = "Nearby Devices", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Text(
                    "No devices found yet. Tap Scan.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                LazyColumn {
                    items(devices, key = { it.address }) { device ->
                        DeviceRow(device = device) {
                            bleViewModel.stopScan()
                            Toast.makeText(context, "Selected ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            // Diagnostics UI (below device list)
            val showDiag by bleViewModel.showDiagnostics.collectAsState()
            if (showDiag) {
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Diagnostics", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                Text("Emulator: ${bleViewModel.isEmulator.collectAsState().value}")
                Text("Advertising supported: ${bleViewModel.advertisingSupported.collectAsState().value}")
                Text("Advertiser available: ${bleViewModel.advertiserAvailable.collectAsState().value}")
                Text("Scanner available: ${bleViewModel.scannerAvailable.collectAsState().value}")
                Text("Status: ${bleViewModel.statusMessage.collectAsState().value}")
                Text("Last error: ${bleViewModel.lastError.collectAsState().value ?: "none"}")

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { bleViewModel.probeSupport() }) {
                        Text("Refresh diagnostics")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = { bleViewModel.setShowDiagnostics(false) }) {
                        Text("Hide")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val logs = bleViewModel.logs.collectAsState().value
                Text("Logs (latest):", style = MaterialTheme.typography.bodySmall)
                logs.take(6).forEach { l ->
                    Text(l, fontSize = 12.sp)
                }
            } else {
                Button(onClick = { bleViewModel.setShowDiagnostics(true) }) {
                    Text("Show diagnostics")
                }
            }
        }
    }
}

@Composable
fun DeviceRow(device: ScannedDevice, onClick: (ScannedDevice) -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick(device) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.avatar),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(device.name ?: "Unknown")
            Text(device.address, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        Text("${device.rssi} dBm", fontSize = 12.sp)
    }
}
