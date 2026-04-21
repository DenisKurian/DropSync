package com.example.test

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.io.File

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

import com.example.test.ui.theme.TestTheme

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
            TestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DropSyncApp()
                }
            }
        }
    }
}

@Composable
fun DropSyncApp() {
    val context = LocalContext.current
    val bleViewModel: BLEViewModel = viewModel()
    val navController = rememberNavController()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (hasAllRequiredPermissions(context)) {
            bleViewModel.startAdvertising()
            bleViewModel.startScan()
        } else {
            Toast.makeText(context, "Permissions NOT granted!", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val missing = requiredPermissionsForThisDevice().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            bleViewModel.startAdvertising()
            bleViewModel.startScan()
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(bleViewModel = bleViewModel, navController = navController)
        }
        composable("chat/{deviceId}") { backStackEntry ->
            val deviceIdStr = backStackEntry.arguments?.getString("deviceId")
            val deviceId = deviceIdStr?.toIntOrNull()
            if (deviceId != null) {
                ChatScreen(deviceId = deviceId, bleViewModel = bleViewModel, navController = navController)
            } else {
                HomeScreen(bleViewModel = bleViewModel, navController = navController)
            }
        }
        composable("files") {
            FilesScreen(navController = navController)
        }
        composable("saved") {
            SavedDevicesScreen(bleViewModel = bleViewModel, navController = navController)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(bleViewModel: BLEViewModel, navController: NavController) {
    val context = LocalContext.current
    val devices by bleViewModel.devices.collectAsState()
    var showPerfDialog by remember { mutableStateOf(false) }

    if (showPerfDialog) {
        val updateTrigger by PerformanceLogger.updateFlow.collectAsState()
        
        AlertDialog(
            onDismissRequest = { showPerfDialog = false },
            title = { Text("IEEE Performance Metrics", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text(
                        text = PerformanceLogger.generateMarkdownTables(),
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, 
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val testNode = devices.firstOrNull()?.nodeId
                            if (testNode != null) {
                                Thread {
                                    for (i in 1..10) {
                                        bleViewModel.sendPerformanceTestMessage(testNode)
                                        Thread.sleep(200)
                                    }
                                }.start()
                                
                                val dummyFile = File(context.cacheDir, "test_payload.bin")
                                if (!dummyFile.exists()) {
                                    dummyFile.writeBytes(ByteArray(1024 * 1024))
                                }
                                bleViewModel.setPendingFile(Uri.fromFile(dummyFile))
                                bleViewModel.startFileTransfer(testNode)
                                Toast.makeText(context, "Started automated test cycle to node $testNode!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No nearby devices to test!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("RUN AUTOMATED TEST")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPerfDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "DropSync", 
                        style = MaterialTheme.typography.displaySmall, 
                        fontWeight = FontWeight.Bold, 
                        color = MaterialTheme.colorScheme.primary
                    ) 
                },
                actions = {
                    TextButton(onClick = { showPerfDialog = true }) {
                        Text("PERF METRICS", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    Toast.makeText(context, "Please select a device from the list to send a file.", Toast.LENGTH_SHORT).show()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Send File", fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { navController.navigate("files") },
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("Received Files", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { navController.navigate("saved") },
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("Saved Devices", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Nearby Devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Text("Scanning for nearby devices...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(devices, key = { it.address }) { device ->
                        DeviceRow(
                            device = device,
                            isSaved = false,
                            onSaveClick = { bleViewModel.saveDevice(device) },
                            onClick = { navController.navigate("chat/${device.nodeId}") }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedDevicesScreen(bleViewModel: BLEViewModel, navController: NavController) {
    val savedDevices by bleViewModel.savedDevices.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Devices", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp).fillMaxSize()) {
            if (savedDevices.isEmpty()) {
                Text(
                    text = "No saved devices.", 
                    fontSize = 14.sp, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(savedDevices, key = { it.nodeId }) { device ->
                        DeviceRow(
                            device = device,
                            isSaved = true,
                            onSaveClick = null,
                            onClick = { navController.navigate("chat/${device.nodeId}") }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(deviceId: Int, bleViewModel: BLEViewModel, navController: NavController) {
    val context = LocalContext.current
    val devices by bleViewModel.devices.collectAsState()
    val savedDevices by bleViewModel.savedDevices.collectAsState()
    val receivedMessages by bleViewModel.receivedMessages.collectAsState()
    val receivedFile by bleViewModel.receivedFileUri.collectAsState()
    
    val targetHex = deviceId.toString(16)
    val chatHistory = receivedMessages.filter { it.startsWith("From $targetHex:") || it.startsWith("To $targetHex:") }
    
    var messageText by remember { mutableStateOf("") }
    
    val isReachable = devices.any { it.nodeId == deviceId }
    val deviceName = savedDevices.find { it.nodeId == deviceId }?.name 
        ?: devices.find { it.nodeId == deviceId }?.name 
        ?: "Device $targetHex"

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (!hasAllRequiredPermissions(context)) {
            Toast.makeText(context, "Grant permissions first!", Toast.LENGTH_SHORT).show()
        } else if (uri != null) {
            bleViewModel.setPendingFile(uri)
            bleViewModel.startFileTransfer(deviceId)
            Toast.makeText(context, "Starting transfer...", Toast.LENGTH_SHORT).show()
        }
    }
    
    val listState = rememberLazyListState()
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isReachable) {
                        TextButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                            Text("SEND FILE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).navigationBarsPadding().fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                bleViewModel.sendMessage(deviceId, messageText)
                                messageText = ""
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Send", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            
            if (receivedFile != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).clickable {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            val parsedUri = Uri.parse(receivedFile!!)
                            val resolvedMime = context.contentResolver.getType(parsedUri) ?: "*/*"
                            setDataAndType(parsedUri, resolvedMime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try { context.startActivity(intent) } catch (e: Exception) {}
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("📥 New file received!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(receivedFile!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
                        }
                        Text("OPEN", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (chatHistory.isEmpty()) {
                    item {
                        Text("No messages yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
                items(chatHistory) { msgLog ->
                    val isSent = msgLog.startsWith("To ")
                    val actualMsg = msgLog.substringAfter(": ")
                    
                    Box(
                        contentAlignment = if (isSent) Alignment.CenterEnd else Alignment.CenterStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Card(
                            shape = RoundedCornerShape(
                                topStart = 16.dp, topEnd = 16.dp,
                                bottomStart = if (isSent) 16.dp else 4.dp,
                                bottomEnd = if (isSent) 4.dp else 16.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Text(
                                text = actualMsg,
                                color = if (isSent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(navController: NavController) {
    val context = LocalContext.current
    val files = remember {
        val picsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "MeshShare")
        val docsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "MeshShare")
        val pics = picsDir.listFiles()?.toList() ?: emptyList()
        val docs = docsDir.listFiles()?.toList() ?: emptyList()
        (pics + docs).sortedByDescending { it.lastModified() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Received Files", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (files.isEmpty()) {
                Text(
                    text = "No files received yet.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(files) { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    val resolvedMime = context.contentResolver.getType(uri) ?: "*/*"
                                    setDataAndType(uri, resolvedMime)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${file.length() / 1024} KB", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            val resolvedMime = context.contentResolver.getType(uri) ?: "*/*"
                                            setDataAndType(uri, resolvedMime)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Open", fontWeight = FontWeight.Bold)
                                }
                            }
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
    isSaved: Boolean = false,
    onSaveClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.avatar),
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = device.name ?: "MESH_NODE",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = device.address,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (!isSaved && onSaveClick != null) {
                Button(
                    onClick = onSaveClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)
                ) {
                    Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            val rssiColor = when {
                device.rssi > -60 -> Color(0xFF00E676)
                device.rssi > -80 -> Color(0xFFFFC107)
                else -> Color(0xFFFF5252)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${device.rssi}",
                    color = rssiColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "dBm",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}