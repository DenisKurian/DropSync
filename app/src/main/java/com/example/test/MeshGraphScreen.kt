package com.example.test

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MeshGraphScreen(viewModel: BLEViewModel) {

    val nodes by viewModel.meshNodes.collectAsState()
    val edges by viewModel.meshEdges.collectAsState()
    val animations by viewModel.packetAnimations.collectAsState()

    Column {

        Text("Mesh Topology")

        Spacer(modifier = Modifier.height(8.dp))

        Text("Nodes: ${nodes.size}")
        Text("Links: ${edges.size}")

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {

            Canvas(modifier = Modifier.fillMaxSize()) {

                val centerX = size.width / 2
                val centerY = size.height / 2
                val radius = size.minDimension / 3

                val nodePositions = mutableMapOf<Int, Offset>()

                nodes.forEachIndexed { index, node ->

                    val angle = (2 * Math.PI / nodes.size) * index

                    val x = centerX + radius * cos(angle).toFloat()
                    val y = centerY + radius * sin(angle).toFloat()

                    nodePositions[node.nodeId] = Offset(x, y)

                    drawCircle(
                        color = Color(0xFF1976D2),
                        radius = 25f,
                        center = Offset(x, y)
                    )
                }

                edges.forEach { edge ->

                    val from = nodePositions[edge.from]
                    val to = nodePositions[edge.to]

                    if (from != null && to != null) {

                        drawLine(
                            color = Color.Gray,
                            start = from,
                            end = to,
                            strokeWidth = 4f
                        )
                    }
                }

                animations.forEach { anim ->

                    val from = nodePositions[anim.from]
                    val to = nodePositions[anim.to]

                    if (from != null && to != null) {

                        val progress = anim.progress

                        val x = from.x + (to.x - from.x) * progress
                        val y = from.y + (to.y - from.y) * progress

                        drawCircle(
                            color = Color.Red,
                            radius = 12f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
    }

    /* -------- Animation Loop -------- */

    LaunchedEffect(animations) {

        while (true) {

            animations.forEach {
                it.progress += 0.05f
            }

            delay(16)
        }
    }
}
