package com.example.test

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class FileClient(private val context: Context) {


    companion object {
        private const val TAG = "FileClient"
        private const val PORT = 8988
        private const val CONNECT_TIMEOUT_MS = 4000
        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_BASE_MS = 700L
    }

    /**
     * Sends file to host over TCP socket.
     */
    fun sendFile(uri: Uri, host: String): Boolean {

        var attempt = 1

        while (attempt <= MAX_ATTEMPTS) {

            var socket: Socket? = null
            var input: InputStream? = null
            var out: OutputStream? = null

            try {

                Log.d(TAG, "Connecting to $host:$PORT (attempt $attempt)")

                socket = Socket()

                socket.connect(
                    InetSocketAddress(host, PORT),
                    CONNECT_TIMEOUT_MS
                )

                socket.tcpNoDelay = true

                Log.d(
                    TAG,
                    "Socket connected local=${socket.localAddress?.hostAddress} remote=${socket.remoteSocketAddress}"
                )

                out = socket.getOutputStream()

                input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open input stream for uri=$uri")

                val buffer = ByteArray(8192)

                var bytesRead: Int
                var totalBytes: Long = 0

                while (true) {

                    bytesRead = input.read(buffer)

                    if (bytesRead == -1) break

                    if (bytesRead > 0) {
                        out.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                }

                out.flush()

                Log.d(
                    TAG,
                    "File sent successfully → uri=$uri bytes=$totalBytes"
                )

                return true

            } catch (e: Exception) {

                Log.e(TAG, "Send attempt $attempt failed", e)

            } finally {

                try { input?.close() } catch (_: Exception) {}
                try { out?.close() } catch (_: Exception) {}
                try { socket?.close() } catch (_: Exception) {}
            }

            if (attempt < MAX_ATTEMPTS) {

                val backoff = BACKOFF_BASE_MS * attempt

                Log.d(TAG, "Retrying in ${backoff}ms")

                try {
                    Thread.sleep(backoff)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }

            attempt++
        }

        Log.e(TAG, "All attempts failed → could not send file to $host")

        return false
    }


}
