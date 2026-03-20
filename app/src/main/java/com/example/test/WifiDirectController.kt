package com.example.test

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.util.Log

class WifiDirectController(private val context: Context) {

    companion object {
        private const val TAG = "WifiDirect"
    }

    private val manager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager

    val channel =
        manager.initialize(context, context.mainLooper, null)

    // Callbacks
    var onConnected: ((String) -> Unit)? = null
    var onGroupOwner: (() -> Unit)? = null
    var onPeersAvailable: ((Collection<WifiP2pDevice>) -> Unit)? = null

    private val receiver = WifiDirectReceiver(manager, channel, this)

    init {

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        }

        context.registerReceiver(receiver, filter)
    }

    fun createGroup() {

        Log.d(TAG, "Creating group")

        manager.createGroup(channel, object : WifiP2pManager.ActionListener {

            override fun onSuccess() {
                Log.d(TAG, "Group creation started")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "createGroup failed $reason")
            }
        })
    }

    fun discoverPeers() {

        Log.d(TAG, "Starting peer discovery")

        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {

            override fun onSuccess() {
                Log.d(TAG, "Peer discovery started")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "discoverPeers failed $reason")
            }
        })
    }

    fun requestPeers() {

        manager.requestPeers(channel) { peers ->
            Log.d(TAG, "Peers found: ${peers.deviceList.size}")
            onPeersAvailable?.invoke(peers.deviceList)
        }
    }

    fun connect(device: WifiP2pDevice) {

        Log.d(TAG, "Connecting to ${device.deviceName} (${device.deviceAddress})")

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress

            // 0 = prefer client, 15 = prefer GO
            groupOwnerIntent = 0
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {

            override fun onSuccess() {
                Log.d(TAG, "Connection initiated")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "connect failed $reason")
            }
        })
    }

    fun disconnect() {

        Log.d(TAG, "Disconnecting / removing group")

        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {

            override fun onSuccess() {
                Log.d(TAG, "Group removed")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "removeGroup failed $reason")
            }
        })
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
    }
}