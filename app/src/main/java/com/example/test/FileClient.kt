package com.example.test

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class FileClient(private val context: Context) {

    companion object {
        private const val TAG = "FileClient"
        private const val PORT = 8988
        private const val CONNECT_TIMEOUT = 4000
        private const val MAX_WAIT = 15000
    }

    fun sendFile(uri: Uri, host: String): Boolean {
        val start = System.currentTimeMillis()
        val displayName = queryDisplayName(uri)
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        while (System.currentTimeMillis() - start < MAX_WAIT) {
            var socket: Socket? = null

            try {
                Log.d(TAG, "Trying connection to $host:$PORT")

                Thread.sleep(1200)

                socket = Socket()
                socket.connect(InetSocketAddress(host, PORT), CONNECT_TIMEOUT)

                Log.d(TAG, "Connected to server")

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Failed to open input stream")

                val input = BufferedInputStream(inputStream)
                val output = DataOutputStream(
                    BufferedOutputStream(socket.getOutputStream())
                )

                output.writeUTF(displayName)
                output.writeUTF(mimeType)

                val buffer = ByteArray(8192)
                var total = 0L

                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    total += read
                }

                output.flush()

                input.close()
                output.close()
                socket.close()

                Log.d(TAG, "File sent successfully ($total bytes)")
                return true

            } catch (e: Exception) {
                Log.e(TAG, "Send attempt failed: ${e.message}")
                try {
                    Thread.sleep(700)
                } catch (_: Exception) {
                }
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }

        Log.e(TAG, "Server never became available")
        return false
    }

    private fun queryDisplayName(uri: Uri): String {
        val fallback = "mesh_${System.currentTimeMillis()}.bin"

        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index) ?: fallback
                } else {
                    fallback
                }
            } ?: fallback
        } catch (e: Exception) {
            Log.w(TAG, "Could not read display name: ${e.message}")
            fallback
        }
    }
}