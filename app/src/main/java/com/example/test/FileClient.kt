package com.example.test

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.net.Socket

class FileClient(private val context: Context) {

    companion object {
        private const val TAG = "FileClient"
        private const val PORT = 8988
        private const val CONNECT_TIMEOUT = 5000
        private const val MAX_WAIT = 15000
    }

    fun sendFile(uri: Uri, host: String): Boolean {
        val start = System.currentTimeMillis()
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri)
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"

        // 1. ALWAYS cache the file locally first. This creates a concrete Flat File
        // guaranteeing 100% integrity offline before socket stream begins.
        // Android 14 on-the-fly transcoders often lie about OpenableColumns.SIZE!
        Log.w(TAG, "Copying URI to cache to determine exact binary byte-size...")
        val actualFile = copyUriToCache(uri)
        if (actualFile == null) {
            Log.e(TAG, "Failed to resolve caching for URI. Aborting.")
            return false
        }
        
        val fileSize = actualFile.length()
        if (fileSize <= 0L) {
            Log.e(TAG, "File is perfectly empty cache. Aborting.")
            actualFile.delete()
            return false
        }

        try {
            while (System.currentTimeMillis() - start < MAX_WAIT) {
                var socket: Socket? = null

                try {
                    Log.d(TAG, "Trying connection to $host:$PORT")

                    Thread.sleep(1000)

                    socket = Socket()
                    socket.connect(InetSocketAddress(host, PORT), CONNECT_TIMEOUT)

                    Log.d(TAG, "Connected to server, sending metadata...")

                    // 2. Safely read stream
                    val inputStream = FileInputStream(actualFile)

                    val input = BufferedInputStream(inputStream)
                    val output = DataOutputStream(
                        BufferedOutputStream(socket.getOutputStream())
                    )

                    // Send metadata
                    output.writeUTF(displayName)
                    output.writeUTF(mimeType)
                    output.writeLong(fileSize)
                    output.flush()

                    Log.d(TAG, "Metadata sent. Starting payload transfer...")

                    // Send payload
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var remaining = fileSize

                    while (remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) break

                        output.write(buffer, 0, read)
                        total += read
                        remaining -= read
                    }

                    output.flush()

                    input.close()
                    output.close()
                    socket.close()

                    if (total > 0L) {
                        Log.d(TAG, "File sent successfully ($total bytes out of expected $fileSize)")
                        return true
                    } else {
                        Log.e(TAG, "File failed to send (0 bytes)")
                        return false
                    }

                } catch (e: Exception) {
                    Log.d(TAG, "Send attempt failed (retrying): ${e.message}")
                    try {
                        Thread.sleep(1000)
                    } catch (ignored: Exception) {}
                } finally {
                    try {
                        socket?.close()
                    } catch (ignored: Exception) {}
                }
            }

            Log.e(TAG, "Server never became available after $MAX_WAIT ms")
            return false
        } finally {
            actualFile.delete()
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        try {
            val tempFile = File(context.cacheDir, "mesh_send_${System.currentTimeMillis()}.tmp")
            context.contentResolver.openInputStream(uri)?.use { inp ->
                tempFile.outputStream().use { out ->
                    inp.copyTo(out)
                }
            }
            return tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed caching URI", e)
            return null
        }
    }

    private fun queryFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else -1L
            } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        val fallback = "mesh_${System.currentTimeMillis()}.bin"
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    var name = cursor.getString(index) ?: fallback
                    name = name.substringAfterLast('/')
                    name
                } else fallback
            } ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }
}