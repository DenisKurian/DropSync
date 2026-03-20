package com.example.test

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log

class WifiDirectReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val controller: WifiDirectController
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "WifiDirect"
    }

    override fun onReceive(context: Context, intent: Intent) {

        when (intent.action) {

            // 🔵 CONNECTION STATE CHANGED
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {

                val networkInfo =
                    intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)

                if (networkInfo?.isConnected == true) {

                    Log.d(TAG, "WiFi Direct connected")

                    manager.requestConnectionInfo(channel) { info ->

                        if (!info.groupFormed) {
                            Log.d(TAG, "Group not formed yet")
                            return@requestConnectionInfo
                        }

                        val host = info.groupOwnerAddress.hostAddress

                        if (info.isGroupOwner) {

                            Log.d(TAG, "I am GROUP OWNER → starting server")

                            FileServer.startServer(context)

                            controller.onGroupOwner?.invoke()

                        } else {

                            Log.d(TAG, "Connected as CLIENT to GO: $host")

                            // 🔴 IMPORTANT: small delay to ensure server is ready
                            Thread.sleep(1200)

                            controller.onConnected?.invoke(host)
                        }
                    }
                } else {
                    Log.d(TAG, "WiFi Direct disconnected")
                }
            }

            // 🟢 PEERS AVAILABLE
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {

                Log.d(TAG, "Peers changed → requesting list")

                // 🔴 DO NOT auto-connect here
                controller.requestPeers()
            }
        }
    }
}