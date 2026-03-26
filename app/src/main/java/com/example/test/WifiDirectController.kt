package com.example.test

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Method

class WifiDirectController(private val context: Context) {

    companion object {
        private const val TAG = "WifiDirect"
        private const val COOLDOWN_MS = 2500L
    }

    private val manager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager

    val channel =
        manager.initialize(context, context.mainLooper, null)

    var onConnected: ((String) -> Unit)? = null
    var onGroupOwner: (() -> Unit)? = null
    var onPeersAvailable: ((Collection<WifiP2pDevice>) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onConnectionFailed: (() -> Unit)? = null

    private val receiver = WifiDirectReceiver(manager, channel, this)
    private val handler = Handler(Looper.getMainLooper())

    private var discovering = false
    private var connecting = false
    private var createGroupInProgress = false
    private var cooldownUntil = 0L

    init {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)
    }

    fun isReady(): Boolean {
        return !discovering &&
                !connecting &&
                !createGroupInProgress &&
                System.currentTimeMillis() >= cooldownUntil
    }

    fun createGroup() {
        if (createGroupInProgress) {
            Log.d(TAG, "Group creation already in progress")
            return
        }

        createGroupInProgress = true
        Log.d(TAG, "Preparing to create group")

        disconnectInternal { 
            actuallyCreateGroup() 
        }
    }

    private fun actuallyCreateGroup() {
        Log.d(TAG, "Creating group")

        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Group creation started successfully")
                createGroupInProgress = false
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "createGroup failed with reason $reason")
                createGroupInProgress = false

                if (reason == WifiP2pManager.BUSY) {
                    cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS
                    handler.postDelayed({ createGroup() }, COOLDOWN_MS)
                    return
                }

                onConnectionFailed?.invoke()
            }
        })
    }

    fun discoverPeers() {
        if (discovering) {
            Log.d(TAG, "Already discovering, skipping")
            return
        }

        discovering = true
        Log.d(TAG, "Starting peer discovery")

        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Peer discovery started")
            }

            override fun onFailure(reason: Int) {
                discovering = false
                Log.e(TAG, "discoverPeers failed with reason $reason")
                // sometimes BUSY here, we could add retry but simple failure is fine for now
            }
        })
    }

    fun requestPeers() {
        manager.requestPeers(channel) { peers ->
            discovering = false
            Log.d(TAG, "Peers found: ${peers.deviceList.size}")
            onPeersAvailable?.invoke(peers.deviceList)
        }
    }

    fun connect(device: WifiP2pDevice) {
        if (connecting) {
            Log.d(TAG, "Already connecting, skipping")
            return
        }

        connecting = true
        Log.d(TAG, "Connecting to ${device.deviceName} (${device.deviceAddress})")

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = 0 // force role as client
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection initiated successfully")
            }

            override fun onFailure(reason: Int) {
                connecting = false
                Log.e(TAG, "connect failed with reason $reason")
                onConnectionFailed?.invoke()
            }
        })
    }

    fun markConnected() {
        connecting = false
    }

    fun disconnect() {
        Log.d(TAG, "Public disconnect() requested")
        disconnectInternal {
            resetState()
            handler.postDelayed({
                onDisconnected?.invoke()
            }, 500L)
        }
    }

    private fun disconnectInternal(onComplete: () -> Unit) {
        Log.d(TAG, "Disconnecting internal / removing group")
        cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS

        manager.cancelConnect(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "cancelConnect success") }
            override fun onFailure(reason: Int) { Log.d(TAG, "cancelConnect failed: $reason") }
        })

        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "removeGroup success")
                forceDeletePersistentGroup() // undocumented robustness fix
                onComplete()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "removeGroup failed $reason (Maybe no group exists)")
                onComplete()
            }
        })
    }
    
    // Deletes persistent groups using reflection to fix Samsung/Nothing connect bugs
    private fun forceDeletePersistentGroup() {
        try {
            val method: Method = manager.javaClass.getMethod("deletePersistentGroup", WifiP2pManager.Channel::class.java, Int::class.javaPrimitiveType, WifiP2pManager.ActionListener::class.java)
            for (netid in 0..31) {
                method.invoke(manager, channel, netid, null)
            }
        } catch (_: Exception) {}
    }

    fun resetState() {
        discovering = false
        connecting = false
        createGroupInProgress = false
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }
}