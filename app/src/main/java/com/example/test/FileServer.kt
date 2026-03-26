package com.example.test

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
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
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "Server READY on port $PORT")

                while (running) {
                    val client = serverSocket!!.accept()
                    client.soTimeout = 20000
                    Log.d(TAG, "Client connected: ${client.inetAddress}")

                    thread {
                        try {
                            val input = DataInputStream(
                                BufferedInputStream(client.getInputStream())
                            )

                            val incomingName = input.readUTF()
                            val mimeType = input.readUTF().ifBlank { "application/octet-stream" }

                            val safeName = sanitizeFileName(incomingName, mimeType)
                            val buffer = ByteArray(8192)
                            var total = 0L

                            val resolver = context.contentResolver

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val values = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                                    put(
                                        MediaStore.MediaColumns.RELATIVE_PATH,
                                        Environment.DIRECTORY_DOWNLOADS + "/MeshDrop"
                                    )
                                }

                                val uri = resolver.insert(
                                    MediaStore.Files.getContentUri("external"),
                                    values
                                ) ?: throw Exception("Failed to create MediaStore entry")

                                val output = BufferedOutputStream(
                                    resolver.openOutputStream(uri)!!
                                )

                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                    total += read
                                }

                                output.flush()
                                output.close()
                                input.close()
                                client.close()

                                Log.d(TAG, "File saved → $uri ($total bytes)")
                                onFileReceived?.invoke(uri.toString())

                            } else {
                                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS
                                )
                                val meshDir = File(downloadsDir, "MeshDrop")
                                if (!meshDir.exists()) meshDir.mkdirs()

                                val outFile = File(meshDir, safeName)
                                val output = BufferedOutputStream(FileOutputStream(outFile))

                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    output.write(buffer, 0, read)
                                    total += read
                                }

                                output.flush()
                                output.close()
                                input.close()
                                client.close()

                                MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(outFile.absolutePath),
                                    arrayOf(mimeType),
                                    null
                                )

                                Log.d(TAG, "File saved → ${outFile.absolutePath} ($total bytes)")
                                onFileReceived?.invoke(outFile.absolutePath)
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "Receive error", e)
                        } finally {
                            try { client.close() } catch (_: Exception) {}
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
        } catch (_: Exception) {
        }

        Log.d(TAG, "Server stopped")
    }

    private fun sanitizeFileName(name: String, mimeType: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
        if (cleaned.isBlank()) {
            return defaultNameForMime(mimeType)
        }

        if (cleaned.contains('.')) return cleaned
        return "$cleaned.bin"
    }

    private fun defaultNameForMime(mimeType: String): String {
        val ext = when {
            mimeType.startsWith("image/") -> "jpg"
            mimeType.startsWith("video/") -> "mp4"
            mimeType.startsWith("audio/") -> "m4a"
            mimeType == "application/pdf" -> "pdf"
            else -> "bin"
        }
        return "mesh_${System.currentTimeMillis()}.$ext"
    }
}