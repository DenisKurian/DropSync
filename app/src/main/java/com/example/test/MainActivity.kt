package com.example.test

import android.Manifest
import android.content.Intent
import android.net.Uri
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



        val nodeId = MeshIdentity.getNodeId(this)
        Log.d("MESH", "My Node ID = ${nodeId.toString(16)}")

        setContent {
            MaterialTheme {
                Surface {
                    DeviceDiscoveryScreen()
                }
            }
        }
    }
}

@Composable
fun DeviceDiscoveryScreen() {

    val bleViewModel: BLEViewModel = viewModel()

    val context = LocalContext.current
    val devices by bleViewModel.devices.collectAsState()
    val receivedFile by bleViewModel.receivedFileUri.collectAsState()

    var selectedNodeId by remember { mutableStateOf<Int?>(null) }
    var permissionsGranted by remember { mutableStateOf(false) }

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null && selectedNodeId != null) {

                bleViewModel.setPendingFile(uri)
                bleViewModel.startFileTransfer(selectedNodeId!!)

                Toast.makeText(
                    context,
                    "Starting mesh transfer...",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    val permissions = remember {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

        } else {

            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->

        permissionsGranted = result.values.all { it }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissions)
    }

    Scaffold(

        floatingActionButton = {

            Column {

                ExtendedFloatingActionButton(

                    onClick = {

                        if (!permissionsGranted) return@ExtendedFloatingActionButton

                        bleViewModel.startAdvertising()
                        bleViewModel.startScan()
                    }

                ) {

                    Text("Advertise + Scan")
                }

                Spacer(modifier = Modifier.height(12.dp))

                ExtendedFloatingActionButton(

                    onClick = {

                        if (selectedNodeId == null) return@ExtendedFloatingActionButton

                        filePickerLauncher.launch(arrayOf("*/*"))
                    }

                ) {

                    Text("Send File")
                }
            }
        }

    ) { innerPadding ->

        Column(

            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)

        ) {

            Text(
                text = "Nearby Mesh Nodes",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty()) {

                Text(
                    text = "No mesh devices discovered yet",
                    fontSize = 14.sp
                )
            }

            LazyColumn {

                items(devices, key = { it.address }) { device ->

                    DeviceRow(device) {

                        selectedNodeId = device.nodeId

                        Toast.makeText(
                            context,
                            "Selected ${device.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            receivedFile?.let { uri ->

                Card {

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {

                        Text("📥 File received")

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(onClick = {

                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(Uri.parse(uri), "image/*")
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                            context.startActivity(intent)

                        }) {

                            Text("Open")
                        }
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

            Text(text = device.name ?: "MESH_NODE")

            Text(
                text = device.address,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "${device.rssi} dBm",
            fontSize = 12.sp
        )
    }
}