package com.appxstudios.festivalconnection.mesh.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
    // GATT clients we've initiated as central. Mirrors iOS BLEService where
    // didDiscover -> connect -> discoverServices -> subscribe to notifications.
    private val connectedClientGatts = ConcurrentHashMap<String, BluetoothGatt>()
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
        // Idempotent — if we're already running, do nothing.
        // This lets MainActivity safely retry start() after onboarding grants permissions.
        if (_isRunning.value) return

        // Defensive guard — silently skip if BLE permissions are missing.
        // The onboarding flow normally grants these before start() is called,
        // but MainActivity may invoke start() before PermissionsScreen completes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w("BLEMeshService", "BLE permissions not granted, deferring start()")
                return
            }
        }

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
        // Tear down any GATT client connections we initiated as central.
        for ((_, gatt) in connectedClientGatts) {
            try {
                gatt.disconnect()
                gatt.close()
            } catch (e: SecurityException) {
                android.util.Log.w("BLEMeshService", "stop: gatt close failed: ${e.message}")
            }
        }
        connectedClientGatts.clear()
        gattServer?.close()
        _isRunning.value = false
        _connectedPeerCount.value = 0
    }

    fun broadcast(data: ByteArray) {
        // Send to all connected GATT clients via notification
        val characteristic = gattServer?.getService(SERVICE_UUID)
            ?.getCharacteristic(CHARACTERISTIC_UUID) ?: return
        for ((_, device) in connectedDevices) {
            notifyDevice(device, characteristic, data)
        }
    }

    /**
     * API-aware notify wrapper.
     *
     * On API 33+ (TIRAMISU) the platform deprecated the legacy
     * `characteristic.value = bytes` setter together with the 3-arg
     * `notifyCharacteristicChanged(...)` and replaced them with a single
     * 4-arg call that takes the value as the last argument. We branch here
     * so newer devices use the modern API while pre-Tiramisu devices still
     * fall back to the deprecated path.
     *
     * The pre-Tiramisu path mutates `characteristic.value`, which is shared
     * state. We synchronize on the characteristic so concurrent broadcasts
     * (or the per-device loop in broadcast()) don't race with each other and
     * end up notifying the wrong bytes.
     */
    private fun notifyDevice(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gattServer?.notifyCharacteristicChanged(device, characteristic, false, value)
        } else {
            synchronized(characteristic) {
                @Suppress("DEPRECATION")
                characteristic.value = value
                @Suppress("DEPRECATION")
                gattServer?.notifyCharacteristicChanged(device, characteristic, false)
            }
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
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address ?: return
            // Skip if we already have a connection in either direction.
            if (connectedClientGatts.containsKey(address)) return
            if (connectedDevices.containsKey(address)) return

            // Connect as GATT client so we receive notifications from this peer.
            // Mirrors iOS centralManager(_:didDiscover:...) -> central.connect(peripheral).
            try {
                val gatt = device.connectGatt(context, false, gattClientCallback)
                if (gatt != null) {
                    connectedClientGatts[address] = gatt
                }
            } catch (e: SecurityException) {
                android.util.Log.w("BLEMeshService", "scan connect failed: ${e.message}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            android.util.Log.w("BLEMeshService", "BLE scan failed: $errorCode")
        }
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address ?: return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    try {
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        android.util.Log.w("BLEMeshService", "discoverServices failed: ${e.message}")
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectedClientGatts.remove(address)
                    try { gatt.close() } catch (_: SecurityException) {}
                }
            } else {
                connectedClientGatts.remove(address)
                try { gatt.close() } catch (_: SecurityException) {}
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt.getService(SERVICE_UUID) ?: return
            val char = service.getCharacteristic(CHARACTERISTIC_UUID) ?: return
            try {
                gatt.setCharacteristicNotification(char, true)
                val cccd = char.getDescriptor(CCCD_UUID)
                if (cccd != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(cccd)
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.w("BLEMeshService", "subscribe failed: ${e.message}")
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // Pre-Tiramisu callback. Newer Android calls the 3-arg overload below.
            val data = characteristic.value ?: return
            processReceivedData(data)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            // API 33+ overload — receives the value as an explicit byte array.
            processReceivedData(value)
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
