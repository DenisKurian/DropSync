//package com.example.test
//
//import android.content.Context
//import android.net.Uri
//
//class FileTransferManager(
//    private val context: Context,
//    private val viewModel: BLEViewModel
//) {
//
//    fun sendFile(uri: Uri, destNodeId: Int) {
//
//        val input =
//            context.contentResolver.openInputStream(uri)
//                ?: return
//
//        val bytes = input.readBytes()
//
//        val chunkSize = BLEConstants.MAX_PAYLOAD_SIZE - 2
//        val totalChunks =
//            (bytes.size + chunkSize - 1) / chunkSize
//
//        val fileId =
//            (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
//
//        val meta =
//            "$fileId|$totalChunks".toByteArray()
//
//        viewModel.sendFileMeta(fileId, meta, destNodeId)
//
//        Thread {
//
//            for (i in 0 until totalChunks) {
//
//                val start = i * chunkSize
//                val end = minOf(start + chunkSize, bytes.size)
//
//                val chunkData =
//                    bytes.copyOfRange(start, end)
//
//                val payload =
//                    ByteArray(2 + chunkData.size)
//
//                payload[0] = i.toByte()
//                payload[1] = totalChunks.toByte()
//
//                System.arraycopy(
//                    chunkData,
//                    0,
//                    payload,
//                    2,
//                    chunkData.size
//                )
//
//                viewModel.sendFileChunk(
//                    fileId,
//                    payload,
//                    destNodeId
//                )
//
//                Thread.sleep(40)
//            }
//        }.start()
//    }
//}