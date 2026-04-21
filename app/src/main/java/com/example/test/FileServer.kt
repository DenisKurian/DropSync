package com.example.test

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket

object FileServer {

    private const val TAG = "FileServer"
    private const val PORT = 8988

    var onFileReceived: ((String) -> Unit)? = null

    @Volatile
    private var isRunning = false
    private var serverThread: Thread? = null
    private var serverSocket: ServerSocket? = null

    fun startServer(context: Context) {
        if (isRunning) {
            Log.d(TAG, "Server is already running")
            return
        }

        isRunning = true
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(PORT).apply { reuseAddress = true }
                Log.d(TAG, "Server READY on port $PORT waiting for clients")

                while (isRunning) {
                    val client = try {
                         serverSocket!!.accept()
                    } catch (e: Exception) {
                         if (isRunning) Log.e(TAG, "Server accept failed", e)
                         break // exit loop if socket is closed
                    }
                    Log.d(TAG, "Client connected")

                    try {
                        val input = BufferedInputStream(client.getInputStream())
                        val dataInput = DataInputStream(input)

                        // 1. Read metadata sent by client
                        val displayName = dataInput.readUTF()
                        val mimeType = dataInput.readUTF()
                        val fileSize = dataInput.readLong()

                        Log.d(TAG, "Receiving file: $displayName, type: $mimeType, size: $fileSize")

                        if (fileSize <= 0) {
                            Log.e(TAG, "Invalid file size received: $fileSize")
                            client.close()
                            continue
                        }

                        // 2. Read fully, decrypt, then save to temp file
                        val tempFile = File(context.cacheDir, "temp_$displayName")

                        val baos = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var remaining = fileSize
                        var totalRead = 0L

                        try {
                            while (remaining > 0) {
                                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                val read = dataInput.read(buffer, 0, toRead)
                                if (read == -1) break
                                
                                baos.write(buffer, 0, read)
                                totalRead += read
                                remaining -= read
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Socket stream broken forcefully by sender (totalRead=$totalRead). Continuing gracefully...")
                        } finally {
                            try { client.close() } catch (e: Exception) {}
                        }

                        if (totalRead != fileSize) {
                            Log.e(TAG, "FATAL: File size mismatch (Expected: $fileSize, Got: $totalRead). Payload truncated by underlying network disconnect. Aborting to prevent image corruption!")
                            tempFile.delete()
                            continue
                        }

                        val receivedBytes = baos.toByteArray()
                        val decryptedBytes = EncryptionUtil.decryptFile(receivedBytes)
                        
                        val output = FileOutputStream(tempFile)
                        output.write(decryptedBytes)
                        output.flush()
                        output.close()

                        Log.d(TAG, "File fully saved temporarily: ${tempFile.absolutePath}")

                        // 3. Insert into MediaStore to make it visible in Gallery exactly as it was originally
                        // 3. Insert into MediaStore dynamically based on media profile!
                        var finalUri: Uri? = null
                        try {
                            val sdkQ = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
                            
                            var resolvedMimeType = mimeType
                            if (resolvedMimeType == "application/octet-stream" || resolvedMimeType.isBlank()) {
                                val extension = File(displayName).extension.lowercase()
                                resolvedMimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
                            }
                            val isMedia = resolvedMimeType.startsWith("image/") || resolvedMimeType.startsWith("video/")

                            if (sdkQ) {
                                val extUri = if (isMedia) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                                val relPath = if (isMedia) Environment.DIRECTORY_PICTURES + "/MeshShare" else Environment.DIRECTORY_DOWNLOADS + "/MeshShare"

                                val resolver = context.contentResolver
                                val values = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, "${System.currentTimeMillis()}_$displayName")
                                    put(MediaStore.MediaColumns.MIME_TYPE, resolvedMimeType)
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                                }
                                
                                val uri = resolver.insert(extUri, values)
                                if (uri != null) {
                                    resolver.openOutputStream(uri)?.use { out ->
                                        tempFile.inputStream().use { inp -> inp.copyTo(out) }
                                    }
                                    values.clear()
                                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                                    resolver.update(uri, values, null, null)
                                    finalUri = uri
                                }
                            }
                            
                            // Complete fallback to manual filesystem
                            if (finalUri == null) {
                                val rootDir = if (isMedia) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
                                val meshDir = File(Environment.getExternalStoragePublicDirectory(rootDir), "MeshShare")
                                
                                if (!meshDir.exists()) meshDir.mkdirs()
                                val destFile = File(meshDir, "${System.currentTimeMillis()}_$displayName")
                                tempFile.copyTo(destFile, overwrite = true)
                                
                                android.media.MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(resolvedMimeType), null)
                                
                                finalUri = try {
                                    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
                                } catch (e: Exception) {
                                    Uri.fromFile(destFile)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "MediaStore save failed heavily", e)
                        }

                        if (finalUri != null) {
                            Log.d(TAG, "File successfully processed and saved visibly: $finalUri")
                            tempFile.delete()
                            // Provide pure content/file uri
                            onFileReceived?.invoke(finalUri.toString())
                        } else {
                            Log.e(TAG, "Failed all graceful UI insertions, pushing temp file directly.")
                            // Construct standard file URI safely
                            onFileReceived?.invoke(Uri.fromFile(tempFile).toString())
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling client connection", e)
                        try { client.close() } catch (ignored: Exception) {}
                    }
                }

            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "Server setup error", e)
            } finally {
                isRunning = false
                serverThread = null
                Log.d(TAG, "Server Thread finished")
            }
        }
        serverThread!!.start()
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket", e)
        }
        serverSocket = null
        serverThread = null
        Log.d(TAG, "Server stopped gracefully")
    }
}