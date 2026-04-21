package com.example.test

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.CopyOnWriteArrayList

object PerformanceLogger {

    data class FileTransferStat(val sizeMb: Double, val transferTimeSec: Double, val throughput: Double)
    val fileStats = CopyOnWriteArrayList<FileTransferStat>()

    val latencyStats = CopyOnWriteArrayList<Long>()
    
    @Volatile var sentCount = 0
    @Volatile var receivedCount = 0

    val updateFlow = MutableStateFlow(0)

    fun logFileTransfer(sizeMb: Double, transferTimeSec: Double, throughput: Double) {
        fileStats.add(FileTransferStat(sizeMb, transferTimeSec, throughput))
        updateFlow.value++
    }

    fun logMessageSent() {
        sentCount++
        updateFlow.value++
    }

    fun logMessageReceived(latency: Long) {
        receivedCount++
        latencyStats.add(latency)
        updateFlow.value++
    }

    fun generateMarkdownTables(): String {
        val sb = StringBuilder()
        sb.append("## Table 1: File Transfer\n")
        sb.append("| File Size (MB) | Transfer Time (s) | Throughput (MB/s) |\n")
        sb.append("|---|---|---|\n")
        var avgTransferTime = 0.0
        var avgThroughput = 0.0
        
        if (fileStats.isNotEmpty()) {
            fileStats.forEach {
                sb.append(String.format("| %.2f | %.1f | %.1f |\n", it.sizeMb, it.transferTimeSec, it.throughput))
                avgTransferTime += it.transferTimeSec
                avgThroughput += it.throughput
            }
            sb.append("\n| **AVG** | **%.1f** | **%.1f** |\n".format(
                avgTransferTime / fileStats.size, 
                avgThroughput / fileStats.size
            ))
        } else {
             sb.append("| N/A | N/A | N/A |\n")
        }

        sb.append("\n## Table 2: BLE Message Latency\n")
        sb.append("| Message | Latency (ms) |\n")
        sb.append("|---|---|\n")
        if (latencyStats.isNotEmpty()) {
            latencyStats.forEachIndexed { i, lat ->
                sb.append("| msg_${i+1} | $lat |\n")
            }
            val avgLatency = latencyStats.average()
            sb.append("\n| **AVG** | **${avgLatency.toInt()}** |\n")
        } else {
             sb.append("| N/A | N/A |\n")
        }

        sb.append("\n## Table 3: Packet Delivery\n")
        val successRate = if (sentCount > 0) (receivedCount.toFloat() / sentCount) * 100 else 0.0f
        sb.append("| Sent | Received | Success Rate (%) |\n")
        sb.append("|---|---|---|\n")
        sb.append(String.format("| %d | %d | %.1f%% |\n", sentCount, receivedCount, successRate))

        return sb.toString()
    }
}
