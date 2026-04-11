import Foundation
import CoreBluetooth
import CryptoKit

// MARK: - BLE Mesh Transport
// Adapted from BitChat BLEService. UUIDs and protocol constants match exactly
// for cross-platform compatibility with the Android implementation.

final class BLEService: NSObject, ObservableObject {

    // MARK: - Constants (verbatim from BitChat reference)
    #if DEBUG
    static let serviceUUID = CBUUID(string: "F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5A")
    #else
    static let serviceUUID = CBUUID(string: "F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5C")
    #endif
    static let characteristicUUID = CBUUID(string: "A1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D")

    static let shared = BLEService()

    private let defaultFragmentSize = 469
    private let bleMaxMTU = 512
    private let messageTTL: UInt8 = 7

    // MARK: - Published State
    @Published private(set) var isRunning = false
    @Published private(set) var connectedPeerCount = 0
    @Published private(set) var bluetoothState: CBManagerState = .unknown

    // MARK: - BLE Managers
    private var centralManager: CBCentralManager?
    private var peripheralManager: CBPeripheralManager?
    private var characteristic: CBMutableCharacteristic?

    // MARK: - Peer Tracking
    private struct PeripheralState {
        let peripheral: CBPeripheral
        var characteristic: CBCharacteristic?
        var peerID: String?
        var isConnected: Bool = false
    }
    private var peripherals: [String: PeripheralState] = [:]
    private var subscribedCentrals: [CBCentral] = []
    private var knownPeers: [String: (name: String, lastSeen: Date)] = [:]

    // MARK: - Message Deduplication
    private var seenMessageIDs: [String: Date] = [:]
    private let dedupWindow: TimeInterval = 300

    // MARK: - Queues
    private let bleQueue = DispatchQueue(label: "fc.ble", qos: .userInitiated)

    // MARK: - Callbacks
    var onPeerDiscovered: ((String, String) -> Void)?  // (publicKeyHex, nickname)
    var onPeerDisconnected: ((String) -> Void)?
    var onPacketReceived: ((Data) -> Void)?

    // MARK: - Identity
    private var myPeerIDData: Data = Data()
    private var myNickname: String = ""

    // MARK: - Init
    private override init() {
        super.init()
    }

    // MARK: - Lifecycle

    func configure(peerIDData: Data, nickname: String) {
        self.myPeerIDData = peerIDData
        self.myNickname = nickname
    }

    func start() {
        guard centralManager == nil else { return }
        bleQueue.async { [weak self] in
            guard let self = self else { return }
            self.centralManager = CBCentralManager(
                delegate: self, queue: self.bleQueue,
                options: [CBCentralManagerOptionRestoreIdentifierKey: "fc.ble.central"]
            )
            self.peripheralManager = CBPeripheralManager(
                delegate: self, queue: self.bleQueue,
                options: [CBPeripheralManagerOptionRestoreIdentifierKey: "fc.ble.peripheral"]
            )
        }
        DispatchQueue.main.async { self.isRunning = true }
    }

    func stop() {
        centralManager?.stopScan()
        peripheralManager?.stopAdvertising()
        DispatchQueue.main.async {
            self.isRunning = false
            self.connectedPeerCount = 0
        }
    }

    // MARK: - Sending

    func broadcast(_ data: Data) {
        // Send to all connected peers via peripheral notifications
        guard let characteristic = characteristic, let peripheralManager = peripheralManager else { return }
        peripheralManager.updateValue(data, for: characteristic, onSubscribedCentrals: nil)

        // Also write to all connected peripherals
        for (_, state) in peripherals where state.isConnected {
            if let char = state.characteristic {
                state.peripheral.writeValue(data, for: char, type: .withResponse)
            }
        }
    }

    func sendPacket(_ packet: CrowdSyncPacket) {
        guard let data = CrowdSyncBinaryProtocol.encode(packet) else { return }
        broadcast(data)
    }

    func sendAnnounce() {
        let payload = myNickname.data(using: .utf8) ?? Data()
        let packet = CrowdSyncPacket(
            type: MessageType.announce.rawValue,
            senderID: myPeerIDData,
            payload: payload,
            ttl: messageTTL
        )
        sendPacket(packet)
    }

    // MARK: - Message Deduplication

    private func isDuplicate(messageID: String) -> Bool {
        if let seen = seenMessageIDs[messageID], Date().timeIntervalSince(seen) < dedupWindow {
            return true
        }
        seenMessageIDs[messageID] = Date()
        // Prune old entries
        let cutoff = Date().addingTimeInterval(-dedupWindow)
        seenMessageIDs = seenMessageIDs.filter { $0.value > cutoff }
        return false
    }

    private func messageID(for data: Data) -> String {
        SHA256.hash(data: data).prefix(16).map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - Packet Processing

    private func processReceivedData(_ data: Data) {
        let msgID = messageID(for: data)
        guard !isDuplicate(messageID: msgID) else { return }

        guard let packet = CrowdSyncBinaryProtocol.decode(data) else { return }

        // Handle announce packets internally
        if packet.type == MessageType.announce.rawValue {
            let senderHex = packet.senderID.map { String(format: "%02x", $0) }.joined()
            let nickname = String(data: packet.payload, encoding: .utf8) ?? "Peer \(senderHex.prefix(4).uppercased())"
            knownPeers[senderHex] = (nickname, Date())
            DispatchQueue.main.async { [weak self] in
                self?.onPeerDiscovered?(senderHex, nickname)
            }
        }

        // Forward to callback
        DispatchQueue.main.async { [weak self] in
            self?.onPacketReceived?(data)
        }

        // Relay if TTL > 1
        if packet.ttl > 1 {
            var relayed = packet
            relayed.ttl = packet.ttl - 1
            if let relayedData = CrowdSyncBinaryProtocol.encode(CrowdSyncPacket(
                type: relayed.type, senderID: relayed.senderID,
                recipientID: relayed.recipientID, timestamp: relayed.timestamp,
                payload: relayed.payload, signature: relayed.signature,
                ttl: relayed.ttl, version: relayed.version
            )) {
                broadcast(relayedData)
            }
        }
    }

    private func updatePeerCount() {
        let count = peripherals.filter { $0.value.isConnected }.count + subscribedCentrals.count
        DispatchQueue.main.async { self.connectedPeerCount = count }
    }
}

// MARK: - CBCentralManagerDelegate

extension BLEService: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        DispatchQueue.main.async { self.bluetoothState = central.state }

        if central.state == .poweredOn {
            central.scanForPeripherals(
                withServices: [BLEService.serviceUUID],
                options: [CBCentralManagerScanOptionAllowDuplicatesKey: false]
            )
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let uuid = peripheral.identifier.uuidString
        guard peripherals[uuid] == nil else { return }

        peripherals[uuid] = PeripheralState(peripheral: peripheral)
        peripheral.delegate = self
        central.connect(peripheral, options: nil)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        let uuid = peripheral.identifier.uuidString
        peripherals[uuid]?.isConnected = true
        peripheral.discoverServices([BLEService.serviceUUID])
        updatePeerCount()
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        let uuid = peripheral.identifier.uuidString
        if let peerID = peripherals[uuid]?.peerID {
            DispatchQueue.main.async { self.onPeerDisconnected?(peerID) }
        }
        peripherals.removeValue(forKey: uuid)
        updatePeerCount()

        // Reconnect after delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) { [weak self] in
            guard let self = self, self.centralManager?.state == .poweredOn else { return }
            self.centralManager?.connect(peripheral, options: nil)
        }
    }

    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        if let peripherals = dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] {
            for peripheral in peripherals {
                let uuid = peripheral.identifier.uuidString
                self.peripherals[uuid] = PeripheralState(peripheral: peripheral, isConnected: peripheral.state == .connected)
                peripheral.delegate = self
            }
        }
    }
}

// MARK: - CBPeripheralDelegate

extension BLEService: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let services = peripheral.services else { return }
        for service in services where service.uuid == BLEService.serviceUUID {
            peripheral.discoverCharacteristics([BLEService.characteristicUUID], for: service)
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let chars = service.characteristics else { return }
        for char in chars where char.uuid == BLEService.characteristicUUID {
            let uuid = peripheral.identifier.uuidString
            peripherals[uuid]?.characteristic = char
            peripheral.setNotifyValue(true, for: char)

            // Send identity announcement
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
                self?.sendAnnounce()
            }
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard let data = characteristic.value, !data.isEmpty else { return }
        processReceivedData(data)
    }
}

// MARK: - CBPeripheralManagerDelegate

extension BLEService: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        if peripheral.state == .poweredOn {
            let char = CBMutableCharacteristic(
                type: BLEService.characteristicUUID,
                properties: [.read, .write, .notify, .writeWithoutResponse],
                value: nil,
                permissions: [.readable, .writeable]
            )
            self.characteristic = char

            let service = CBMutableService(type: BLEService.serviceUUID, primary: true)
            service.characteristics = [char]
            peripheral.add(service)
            peripheral.startAdvertising([
                CBAdvertisementDataServiceUUIDsKey: [BLEService.serviceUUID]
            ])
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        if !subscribedCentrals.contains(where: { $0.identifier == central.identifier }) {
            subscribedCentrals.append(central)
        }
        updatePeerCount()
        sendAnnounce()
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        subscribedCentrals.removeAll { $0.identifier == central.identifier }
        updatePeerCount()
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            if let data = request.value, !data.isEmpty {
                processReceivedData(data)
            }
            peripheral.respond(to: request, withResult: .success)
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, willRestoreState dict: [String: Any]) {
        // Restore advertising state
    }
}
