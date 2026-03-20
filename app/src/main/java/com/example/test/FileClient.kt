package com.example.test

import android.content.Context
import android.net.Uri
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

        while (System.currentTimeMillis() - start < MAX_WAIT) {

            try {

                Log.d(TAG, "Trying connection to $host:$PORT")

                val socket = Socket()

                socket.connect(
                    InetSocketAddress(host, PORT),
                    CONNECT_TIMEOUT
                )

                Log.d(TAG, "Connected to server")

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Failed to open input stream")

                val input = BufferedInputStream(inputStream)

                val output = DataOutputStream(
                    BufferedOutputStream(socket.getOutputStream())
                )

                // 🔴 FIX: handle file size safely
                val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                val fileSize = afd?.length ?: -1

                if (fileSize <= 0) {
                    throw Exception("Invalid file size: $fileSize")
                }

                Log.d(TAG, "Sending file size: $fileSize")

                output.writeLong(fileSize)

                val buffer = ByteArray(8192)

                var total = 0L

                while (true) {

                    val read = input.read(buffer)

                    if (read == -1) break

                    output.write(buffer, 0, read)

                    total += read

                    Log.d(TAG, "Progress: $total / $fileSize")
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
                } catch (_: Exception) {}
            }
        }

        Log.e(TAG, "Server never became available")

        return false
    }
}