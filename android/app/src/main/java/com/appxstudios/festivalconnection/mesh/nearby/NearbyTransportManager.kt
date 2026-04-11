package com.appxstudios.festivalconnection.mesh.nearby

import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Android peer-to-peer wireless transport.
// Uses Wi-Fi Direct for Android-to-Android communication without internet.
// Runs in parallel with BLE — messages are deduplicated across transports.

class NearbyTransportManager(private val context: Context) {

    private val _connectedPeerCount = MutableStateFlow(0)
    val connectedPeerCount: StateFlow<Int> = _connectedPeerCount

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var wifiP2pManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null

    var onDataReceived: ((ByteArray, String) -> Unit)? = null
    var onPeerConnected: ((String) -> Unit)? = null
    var onPeerDisconnected: ((String) -> Unit)? = null

    fun start() {
        wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = wifiP2pManager?.initialize(context, context.mainLooper, null)

        wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _isRunning.value = true
            }
            override fun onFailure(reason: Int) {
                // Wi-Fi Direct not available or permissions denied
            }
        })
    }

    fun stop() {
        wifiP2pManager?.removeGroup(channel, null)
        _isRunning.value = false
        _connectedPeerCount.value = 0
    }

    fun send(data: ByteArray) {
        // Send via Wi-Fi Direct socket to connected peers
        // Implementation depends on established socket connections
    }
}
