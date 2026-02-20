package com.example.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MeshMonitorScreen(
    vm: BLEViewModel = viewModel()
) {

    val events by vm.meshEvents.collectAsState()

    var messageText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {

        Text(
            text = "Mesh Network Monitor",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(8.dp))

        Row {

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                label = { Text("Message") }
            )

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        vm.sendMessage(messageText)
                        messageText = ""
                    }
                }
            ) {
                Text("Send")
            }
        }

        Spacer(Modifier.height(12.dp))

        Divider()

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {

            items(events) { event ->
                MeshEventItem(event)
            }
        }
    }
}

@Composable
private fun MeshEventItem(event: MeshEvent) {

    val time = remember(event.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date(event.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {

        Column(
            modifier = Modifier
                .padding(12.dp)
        ) {

            Text(
                text = "Node: ${event.srcNodeId.toString(16)}",
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = event.message,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text("TTL: ${event.ttl}")
                Text(time)
            }
        }
    }
}
