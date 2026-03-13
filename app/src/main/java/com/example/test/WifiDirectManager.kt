package com.example.test

import android.content.Context
import android.net.wifi.p2p.*
import android.os.Handler
import android.os.Looper
import android.util.Log

class WifiDirectManager(private val context: Context) {


    companion object {
        private const val TAG = "WifiDirect"
    }

    private val manager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager

    private val channel =
        manager.initialize(context, Looper.getMainLooper(), null)

    private val handler = Handler(Looper.getMainLooper())

    var onConnected: ((String) -> Unit)? = null
    var onGroupOwner: (() -> Unit)? = null

    private var notifiedGroupOwner = false
    private var notifiedConnectedHost: String? = null

    /* ================= RECEIVER ROLE ================= */

    fun createGroup() {

        Log.d(TAG, "Creating WiFi Direct group (receiver mode)")

        manager.createGroup(channel, object : WifiP2pManager.ActionListener {

            override fun onSuccess() {

                Log.d(TAG, "Group creation requested")
                pollConnectionInfo()
            }

            override fun onFailure(reason: Int) {

                Log.e(TAG, "Group creation failed $reason")

                handler.postDelayed({

                    manager.removeGroup(channel,
                        object : WifiP2pManager.ActionListener {

                            override fun onSuccess() {

                                Log.d(TAG, "Removed stale group, retrying create")
                                handler.postDelayed({ createGroup() }, 800)
                            }

                            override fun onFailure(reason: Int) {

                                Log.e(TAG, "Remove group failed $reason")
                                handler.postDelayed({ createGroup() }, 1200)
                            }
                        })

                }, 500)
            }
        })
    }

    /* ================= SENDER ROLE ================= */

    fun discoverAndConnect() {

        notifiedConnectedHost = null
        notifiedGroupOwner = false

        discoverPeers()
    }

    /* ================= DISCOVERY ================= */

    private fun discoverPeers() {

        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {

            override fun onSuccess() {

                Log.d(TAG, "Peer discovery started")
                handler.postDelayed({ requestPeers() }, 1200)
            }

            override fun onFailure(reason: Int) {

                Log.e(TAG, "Discovery failed $reason")
                handler.postDelayed({ discoverPeers() }, 2000)
            }
        })
    }

    /* ================= REQUEST PEERS ================= */

    private fun requestPeers() {

        manager.requestPeers(channel) { peers ->

            val list = peers.deviceList.toList()

            if (list.isEmpty()) {

                Log.d(TAG, "No peers yet, retrying")
                handler.postDelayed({ requestPeers() }, 1200)
                return@requestPeers
            }

            Log.d(TAG, "Peers discovered: ${list.size}")

            list.forEach {
                Log.d(TAG, "Peer: ${it.deviceName} ${it.deviceAddress}")
            }

            val device = list.first()

            Log.d(TAG, "Connecting to ${device.deviceName}")

            connect(device)
        }
    }

    /* ================= CONNECT ================= */

    private fun connect(device: WifiP2pDevice) {

        val config = WifiP2pConfig().apply {

            deviceAddress = device.deviceAddress

            // encourage this device to be CLIENT
            groupOwnerIntent = 0
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {

            override fun onSuccess() {

                Log.d(TAG, "Connection initiated")
                pollConnectionInfo()
            }

            override fun onFailure(reason: Int) {

                Log.e(TAG, "Connection failed $reason")
                handler.postDelayed({ discoverPeers() }, 1500)
            }
        })
    }

    /* ================= CONNECTION INFO ================= */

    private fun pollConnectionInfo() {

        handler.post(object : Runnable {

            override fun run() {

                try {

                    manager.requestConnectionInfo(channel) { info ->

                        if (!info.groupFormed) {
                            handler.postDelayed(this, 800)
                            return@requestConnectionInfo
                        }

                        val host = info.groupOwnerAddress?.hostAddress

                        if (info.isGroupOwner) {

                            if (!notifiedGroupOwner) {

                                notifiedGroupOwner = true

                                Log.d(TAG, "I am Group Owner → waiting for incoming file")

                                FileServer.startServer(context)

                                onGroupOwner?.invoke()
                            }

                            // keep polling while GO
                            handler.postDelayed(this, 1500)

                        } else {

                            if (host == null) {
                                Log.d(TAG, "Client waiting for GO IP...")
                                handler.postDelayed(this, 800)
                                return@requestConnectionInfo
                            }

                            if (host != notifiedConnectedHost) {

                                notifiedConnectedHost = host

                                Log.d(TAG, "I am Client → sending file to GO: $host")

                                onConnected?.invoke(host)
                            }
                        }
                    }

                } catch (e: Exception) {

                    Log.e(TAG, "Connection polling error", e)
                    handler.postDelayed(this, 1000)
                }
            }
        })
    }

    /* ================= DISCONNECT ================= */

    fun disconnect() {

        try {

            manager.removeGroup(channel, object : WifiP2pManager.ActionListener {

                override fun onSuccess() {

                    Log.d(TAG, "WiFi Direct group removed")

                    notifiedGroupOwner = false
                    notifiedConnectedHost = null
                }

                override fun onFailure(reason: Int) {

                    Log.e(TAG, "Remove group failed $reason")
                }
            })

        } catch (e: Exception) {

            Log.e(TAG, "Disconnect error", e)
        }
    }


}
