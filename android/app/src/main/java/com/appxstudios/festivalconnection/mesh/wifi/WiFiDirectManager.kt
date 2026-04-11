package com.appxstudios.festivalconnection.mesh.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

// Wi-Fi Direct P2P transport for Android-to-Android mesh communication.
// Runs in parallel with BLE — messages are deduplicated across transports.

class WiFiDirectManager(private val context: Context) {

    companion object {
        private const val TAG = "WiFiDirectManager"
        private const val PORT = 8765
        private const val SOCKET_TIMEOUT = 10000
    }

    private val _connectedPeerCount = MutableStateFlow(0)
    val connectedPeerCount: StateFlow<Int> = _connectedPeerCount

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var wifiP2pManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val connectedSockets = mutableListOf<Socket>()
    private var serverSocket: ServerSocket? = null

    var onDataReceived: ((ByteArray, String) -> Unit)? = null
    var onPeerConnected: ((String) -> Unit)? = null
    var onPeerDisconnected: ((String) -> Unit)? = null

    @Suppress("MissingPermission")
    fun start() {
        wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = wifiP2pManager?.initialize(context, context.mainLooper, null)

        registerReceiver()
        startDiscovery()
        startServer()
        _isRunning.value = true
    }

    fun stop() {
        wifiP2pManager?.removeGroup(channel, null)
        wifiP2pManager?.stopPeerDiscovery(channel, null)
        unregisterReceiver()
        serverSocket?.close()
        connectedSockets.forEach { it.close() }
        connectedSockets.clear()
        scope.cancel()
        _isRunning.value = false
        _connectedPeerCount.value = 0
    }

    fun sendPacket(data: ByteArray) {
        scope.launch {
            val sockets = synchronized(connectedSockets) { connectedSockets.toList() }
            for (socket in sockets) {
                try {
                    val out = DataOutputStream(socket.getOutputStream())
                    out.writeInt(data.size)
                    out.write(data)
                    out.flush()
                } catch (e: Exception) {
                    Log.w(TAG, "Send failed: ${e.message}")
                    synchronized(connectedSockets) { connectedSockets.remove(socket) }
                    _connectedPeerCount.value = connectedSockets.size
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun startDiscovery() {
        wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "Peer discovery started") }
            override fun onFailure(reason: Int) { Log.w(TAG, "Peer discovery failed: $reason") }
        })
    }

    private fun startServer() {
        scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "Server listening on port $PORT")
                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    synchronized(connectedSockets) { connectedSockets.add(client) }
                    _connectedPeerCount.value = connectedSockets.size
                    onPeerConnected?.invoke(client.inetAddress.hostAddress ?: "unknown")
                    handleClient(client)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Server error: ${e.message}")
            }
        }
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                val input = DataInputStream(socket.getInputStream())
                while (isActive && !socket.isClosed) {
                    val length = input.readInt()
                    if (length in 1..65536) {
                        val data = ByteArray(length)
                        input.readFully(data)
                        val peerId = socket.inetAddress.hostAddress ?: "unknown"
                        onDataReceived?.invoke(data, peerId)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Client disconnected: ${e.message}")
            } finally {
                synchronized(connectedSockets) { connectedSockets.remove(socket) }
                _connectedPeerCount.value = connectedSockets.size
                onPeerDisconnected?.invoke(socket.inetAddress?.hostAddress ?: "unknown")
            }
        }
    }

    @Suppress("MissingPermission")
    private fun connectToPeer(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() { Log.d(TAG, "Connecting to ${device.deviceName}") }
            override fun onFailure(reason: Int) { Log.w(TAG, "Connect failed: $reason") }
        })
    }

    private fun connectToGroupOwner(info: WifiP2pInfo) {
        if (!info.isGroupOwner && info.groupOwnerAddress != null) {
            scope.launch {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(info.groupOwnerAddress, PORT), SOCKET_TIMEOUT)
                    synchronized(connectedSockets) { connectedSockets.add(socket) }
                    _connectedPeerCount.value = connectedSockets.size
                    onPeerConnected?.invoke(info.groupOwnerAddress.hostAddress ?: "unknown")
                    handleClient(socket)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to connect to group owner: ${e.message}")
                }
            }
        }
    }

    private fun registerReceiver() {
        receiver = object : BroadcastReceiver() {
            @Suppress("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        wifiP2pManager?.requestPeers(channel) { peers: WifiP2pDeviceList ->
                            for (device in peers.deviceList) {
                                if (device.status == WifiP2pDevice.AVAILABLE) {
                                    connectToPeer(device)
                                }
                            }
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        wifiP2pManager?.requestConnectionInfo(channel) { info: WifiP2pInfo ->
                            if (info.groupFormed) connectToGroupOwner(info)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    private fun unregisterReceiver() {
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
    }
}
