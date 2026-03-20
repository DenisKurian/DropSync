package com.example.test

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.net.ServerSocket
import kotlin.concurrent.thread

object FileServer {

    private const val TAG = "FileServer"
    private const val PORT = 8988

    private var serverSocket: ServerSocket? = null
    private var running = false

    var onFileReceived: ((String) -> Unit)? = null

    @Synchronized
    fun startServer(context: Context) {

        if (running) {
            Log.d(TAG, "Server already running")
            return
        }

        running = true

        thread {

            try {

                Log.d(TAG, "Binding server socket...")
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "Server READY on port $PORT")

                while (running) {

                    Log.d(TAG, "Waiting for client...")
                    val client = serverSocket!!.accept()
                    client.soTimeout = 20000

                    Log.d(TAG, "Client connected: ${client.inetAddress}")

                    thread {

                        try {

                            val input = DataInputStream(
                                BufferedInputStream(client.getInputStream())
                            )

                            val fileSize = input.readLong()

                            Log.d(TAG, "Receiving file size: $fileSize")

                            if (fileSize <= 0 || fileSize > 100_000_000) {
                                throw Exception("Invalid file size: $fileSize")
                            }

                            val fileName = "mesh_${System.currentTimeMillis()}.jpg"

                            val values = ContentValues().apply {
                                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                put(
                                    MediaStore.Images.Media.RELATIVE_PATH,
                                    Environment.DIRECTORY_PICTURES + "/MeshDrop"
                                )
                            }

                            val resolver = context.contentResolver

                            val uri = resolver.insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                values
                            ) ?: throw Exception("Failed to create MediaStore entry")

                            val output = BufferedOutputStream(
                                resolver.openOutputStream(uri)!!
                            )

                            val buffer = ByteArray(8192)

                            var remaining = fileSize
                            var total = 0L

                            while (remaining > 0) {

                                val read = input.read(
                                    buffer,
                                    0,
                                    minOf(buffer.size.toLong(), remaining).toInt()
                                )

                                if (read == -1) {
                                    throw Exception("Stream ended early. Remaining: $remaining")
                                }

                                output.write(buffer, 0, read)

                                total += read
                                remaining -= read

                                Log.d(TAG, "Progress: $total / $fileSize")
                            }

                            output.flush()
                            output.close()
                            input.close()
                            client.close()

                            Log.d(TAG, "File saved → $uri ($total bytes)")

                            onFileReceived?.invoke(uri.toString())

                        } catch (e: Exception) {

                            Log.e(TAG, "Client receive error", e)
                        }
                    }
                }

            } catch (e: Exception) {

                Log.e(TAG, "Server error", e)
            }
        }
    }

    fun stopServer() {

        running = false

        try {
            serverSocket?.close()
        } catch (_: Exception) {}

        Log.d(TAG, "Server stopped")
    }
}