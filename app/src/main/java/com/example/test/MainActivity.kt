package com.example.test

import android.Manifest
import android.bluetooth.BluetoothManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

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
                    DeviceDiscoveryScreen(
                        advertiser = bleAdvertiser
                    )
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

    // -------- Permissions --------
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result: Map<String, Boolean> ->
        if (!result.values.all { it }) {
            Toast.makeText(context, "Permissions required for BLE", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissions)
    }

    // -------- UI --------
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
                            onFailed = { code: Int, message: String ->
                                Toast.makeText(
                                    context,
                                    "Advertise failed ($code): $message",
                                    Toast.LENGTH_LONG
                                ).show()
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
                            Toast.makeText(context, "Enable Bluetooth", Toast.LENGTH_SHORT).show()
                            return@ExtendedFloatingActionButton
                        }

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

            Text(
                text = "Nearby Devices",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Text(
                    text = "No devices found yet. Tap Scan.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                LazyColumn {
                    items(
                        items = devices,
                        key = { device: ScannedDevice -> device.address }
                    ) { device: ScannedDevice ->
                        DeviceRow(
                            device = device,
                            onClick = { selected: ScannedDevice ->
                                bleViewModel.stopScan()
                                Toast.makeText(
                                    context,
                                    "Selected ${selected.name ?: selected.address}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceRow(
    device: ScannedDevice,
    onClick: (ScannedDevice) -> Unit
) {
    Row(
        modifier = Modifier
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

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(text = device.name ?: "Unknown")
            Text(text = device.address, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(text = "${device.rssi} dBm", fontSize = 12.sp)
    }
}
