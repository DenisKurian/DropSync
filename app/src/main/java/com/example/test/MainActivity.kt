package com.example.test

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val advertiser = BLEAdvertiser(this)

        val nodeId = MeshIdentity.getNodeId(this)
        Log.d("MESH", "My Node ID = ${nodeId.toString(16)}")

        setContent {
            MaterialTheme {
                Surface {
                    DeviceDiscoveryScreen(advertiser = advertiser)
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
    val meshEvents by bleViewModel.meshEvents.collectAsState()

    /* -------- Permissions -------- */

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
    ) { result ->
        if (!result.values.all { it }) {
            Toast.makeText(context, "BLE permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissions)
    }

    /* -------- UI -------- */

    Scaffold(
        floatingActionButton = {

            Column {

                ExtendedFloatingActionButton(
                    onClick = {
                        advertiser.startHelloLoop()
                        Toast.makeText(context, "HELLO loop started", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Advertise")
                }

                Spacer(modifier = Modifier.height(12.dp))

                ExtendedFloatingActionButton(
                    onClick = {
                        if (!bleViewModel.isBluetoothEnabled()) {
                            Toast.makeText(context, "Enable Bluetooth", Toast.LENGTH_SHORT).show()
                            return@ExtendedFloatingActionButton
                        }
                        bleViewModel.startScan(300_000L)
                    }
                ) {
                    Text("Scan")
                }

                Spacer(modifier = Modifier.height(12.dp))

                ExtendedFloatingActionButton(
                    onClick = {
                        bleViewModel.sendMessage("Hi Mesh")
                        Toast.makeText(context, "Message sent", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Send Test Message")
                }
            }
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {

            /* -------- Nearby Nodes -------- */

            Text(
                text = "Nearby Mesh Nodes",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Text(
                    text = "No nodes found. Tap Scan.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
                LazyColumn {
                    items(devices, key = { it.address }) { device ->
                        DeviceRow(device) {
                            bleViewModel.stopScan()
                            Toast.makeText(
                                context,
                                "Selected ${device.name}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            /* -------- Mesh Activity -------- */

            Spacer(modifier = Modifier.height(20.dp))

            Divider()

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Mesh Network Activity",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {

                items(meshEvents) { event ->

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {

                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {

                            Text(
                                text = "Node: ${event.srcNodeId.toString(16)}",
                                style = MaterialTheme.typography.labelLarge
                            )

                            Spacer(Modifier.height(4.dp))

                            Text(event.message)

                            Spacer(Modifier.height(4.dp))

                            Text("TTL: ${event.ttl}")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            /* -------- Graph Visualization -------- */

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(bottom = 120.dp) // space for FABs
            ) {
                MeshGraphScreen(viewModel = bleViewModel)
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
            Text(text = device.name ?: "MESH_NODE")
            Text(text = device.address, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(text = "${device.rssi} dBm", fontSize = 12.sp)
    }
}
