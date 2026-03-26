package com.example.test

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

private fun requiredPermissionsForThisDevice(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        else -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}

private fun hasAllRequiredPermissions(context: android.content.Context): Boolean {
    return requiredPermissionsForThisDevice().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

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

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (!hasAllRequiredPermissions(context)) {
                Toast.makeText(context, "Grant permissions first!", Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }

            if (uri != null && selectedNodeId != null) {
                bleViewModel.setPendingFile(uri)
                bleViewModel.startFileTransfer(selectedNodeId!!)
                Toast.makeText(context, "Starting mesh transfer...", Toast.LENGTH_SHORT).show()
            }
        }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = hasAllRequiredPermissions(context)

        Log.d("PERMISSION", "Result = $result")
        Log.d("PERMISSION", "Final status = $granted")

        if (!granted) {
            Toast.makeText(context, "Permissions NOT granted!", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val missing = requiredPermissionsForThisDevice().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    Scaffold(
        floatingActionButton = {
            Column {
                ExtendedFloatingActionButton(
                    onClick = {
                        val missing = requiredPermissionsForThisDevice().filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }

                        if (missing.isNotEmpty()) {
                            Toast.makeText(context, "Grant permissions first!", Toast.LENGTH_SHORT).show()
                            permissionLauncher.launch(missing.toTypedArray())
                            return@ExtendedFloatingActionButton
                        }

                        bleViewModel.startAdvertising()
                        bleViewModel.startScan()
                    }
                ) {
                    Text("Advertise + Scan")
                }

                Spacer(modifier = Modifier.height(12.dp))

                ExtendedFloatingActionButton(
                    onClick = {
                        val missing = requiredPermissionsForThisDevice().filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }

                        if (missing.isNotEmpty()) {
                            Toast.makeText(context, "Grant permissions first!", Toast.LENGTH_SHORT).show()
                            permissionLauncher.launch(missing.toTypedArray())
                            return@ExtendedFloatingActionButton
                        }

                        if (selectedNodeId == null) {
                            Toast.makeText(context, "Select a device first", Toast.LENGTH_SHORT).show()
                            return@ExtendedFloatingActionButton
                        }

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
                        Text(text = uri, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                val parsedUri = Uri.parse(uri)
                                val resolvedMime = context.contentResolver.getType(parsedUri) ?: "*/*"
                                setDataAndType(parsedUri, resolvedMime)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }) {
                            Text("Open")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                try {
                    val picsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "MeshShare")
                    val docsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "MeshShare")
                    val picsCount = picsDir.listFiles()?.size ?: 0
                    val docsCount = docsDir.listFiles()?.size ?: 0
                    val total = picsCount + docsCount
                    
                    if (total > 0) {
                        Toast.makeText(context, "Saved Successfully! \\nPictures/MeshShare: $picsCount items\\nDownloads/MeshShare: $docsCount items", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "No files found yet in public directories.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Storage folder generated but scanning requires external file manager.", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Check Saved Files (Storage)")
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