package com.appxstudios.festivalconnection.mesh.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import com.appxstudios.festivalconnection.BuildConfig
import com.appxstudios.festivalconnection.protocol.CrowdSyncBinaryProtocol
import com.appxstudios.festivalconnection.protocol.CrowdSyncPacket
import com.appxstudios.festivalconnection.protocol.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// BLE Mesh Transport — UUIDs match iOS and BitChat exactly for cross-platform compatibility
@SuppressLint("MissingPermission")
class BLEMeshService(private val context: Context) {

    companion object {
        // Exact BitChat UUIDs for cross-platform compatibility.
        // Debug builds use a separate UUID so debug installs don't see release peers
        // and vice versa — this matches iOS BLEService.swift which does the same.
        val SERVICE_UUID: UUID = if (BuildConfig.DEBUG) {
            UUID.fromString("F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5A")
        } else {
            UUID.fromString("F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5C")
        }
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("A1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val MESSAGE_TTL: Byte = 7
    }

    private val _connectedPeerCount = MutableStateFlow(0)
    val connectedPeerCount: StateFlow<Int> = _connectedPeerCount

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null

    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private val seenMessageIDs = ConcurrentHashMap<String, Long>()
    private val dedupWindowMs = 300_000L

    // Callbacks
    var onPeerDiscovered: ((String, String) -> Unit)? = null
    var onPacketReceived: ((ByteArray) -> Unit)? = null

    private var myPeerID: ByteArray = ByteArray(8)
    private var myNickname: String = ""

    fun configure(peerID: ByteArray, nickname: String) {
        myPeerID = peerID
        myNickname = nickname
    }

    fun start() {
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter ?: return

        startGattServer()
        startAdvertising()
        startScanning()
        _isRunning.value = true
    }

    fun stop() {
        bleScanner?.stopScan(scanCallback)
        bleAdvertiser?.stopAdvertising(advertiseCallback)
        gattServer?.close()
        _isRunning.value = false
        _connectedPeerCount.value = 0
    }

    fun broadcast(data: ByteArray) {
        // Send to all connected GATT clients via notification
        val characteristic = gattServer?.getService(SERVICE_UUID)
            ?.getCharacteristic(CHARACTERISTIC_UUID) ?: return
        characteristic.value = data
        for ((_, device) in connectedDevices) {
            gattServer?.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    fun sendPacket(packet: CrowdSyncPacket) {
        val data = CrowdSyncBinaryProtocol.encode(packet) ?: return
        broadcast(data)
    }

    // MARK: - GATT Server

    private fun startGattServer() {
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)

        val characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or
                BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(cccd)

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(characteristic)
        gattServer?.addService(service)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevices[device.address] = device
                _connectedPeerCount.value = connectedDevices.size
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevices.remove(device.address)
                _connectedPeerCount.value = connectedDevices.size
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            processReceivedData(value)
        }
    }

    // MARK: - Advertising

    private fun startAdvertising() {
        bleAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: return
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()
        bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {}
        override fun onStartFailure(errorCode: Int) {}
    }

    // MARK: - Scanning

    private fun startScanning() {
        bleScanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bleScanner?.startScan(listOf(filter), settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Connection to discovered peripherals handled by GATT server accepting connections
        }
    }

    // MARK: - Message Processing

    private fun processReceivedData(data: ByteArray) {
        val msgID = computeMessageID(data)
        if (isDuplicate(msgID)) return

        val packet = CrowdSyncBinaryProtocol.decode(data) ?: return

        // Handle announce
        if (packet.type == MessageType.ANNOUNCE.value) {
            val senderHex = packet.senderID.joinToString("") { "%02x".format(it) }
            val nickname = String(packet.payload, Charsets.UTF_8).ifEmpty {
                "Peer ${senderHex.take(4).uppercase()}"
            }
            onPeerDiscovered?.invoke(senderHex, nickname)
        }

        onPacketReceived?.invoke(data)

        // Relay if TTL > 1
        if (packet.ttl > 1) {
            val relayed = packet.copy(ttl = packet.ttl - 1)
            CrowdSyncBinaryProtocol.encode(relayed)?.let { broadcast(it) }
        }
    }

    private fun computeMessageID(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    private fun isDuplicate(id: String): Boolean {
        val now = System.currentTimeMillis()
        if (seenMessageIDs.containsKey(id) && (now - (seenMessageIDs[id] ?: 0)) < dedupWindowMs) {
            return true
        }
        seenMessageIDs[id] = now
        // Prune
        val cutoff = now - dedupWindowMs
        seenMessageIDs.entries.removeAll { it.value < cutoff }
        return false
    }
}
