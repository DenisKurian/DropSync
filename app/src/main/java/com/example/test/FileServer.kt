package com.example.test

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.net.ServerSocket
import kotlin.concurrent.thread

object FileServer {

    private const val TAG = "FileServer"
    private const val PORT = 8988

    private var serverSocket: ServerSocket? = null
    private var running = false

    // UI notification callback
    var onFileReceived: ((String) -> Unit)? = null

    fun startServer(context: Context) {

        if (running) {
            Log.d(TAG, "Server already running")
            return
        }

        running = true

        thread {

            try {

                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "Server started on port $PORT")

                while (running) {

                    val client = serverSocket!!.accept()
                    Log.d(TAG, "Client connected: ${client.inetAddress}")

                    thread {

                        try {

                            val input = client.getInputStream()

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
                            )

                            if (uri == null) {
                                Log.e(TAG, "Failed to create MediaStore entry")
                                return@thread
                            }

                            val output = resolver.openOutputStream(uri)

                            val buffer = ByteArray(8192)
                            var read: Int

                            while (true) {

                                read = input.read(buffer)

                                if (read == -1) break

                                output!!.write(buffer, 0, read)
                            }

                            output!!.flush()
                            output.close()
                            input.close()
                            client.close()

                            Log.d(TAG, "File saved to gallery: $uri")

                            // notify UI
                            onFileReceived?.invoke(uri.toString())

                        } catch (e: Exception) {
                            Log.e(TAG, "Client handling error", e)
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