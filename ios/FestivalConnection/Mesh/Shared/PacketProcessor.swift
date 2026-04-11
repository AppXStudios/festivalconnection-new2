import Foundation
import CryptoKit

// MARK: - Packet Processor
// Central router that receives raw bytes from all transports (BLE, Multipeer, Nostr),
// deduplicates by message ID, decrements TTL and relays, and routes to handlers.

final class PacketProcessor: ObservableObject {
    static let shared = PacketProcessor()

    // Message deduplication cache
    private var seenIDs: [String: Date] = [:]
    private let dedupWindow: TimeInterval = 300
    private let maxCacheSize = 1000

    // Callbacks for routing by message type
    var onAnnounce: ((String, String, Date) -> Void)?         // peerKeyHex, nickname, timestamp
    var onMessage: ((String, String, String, Date) -> Void)?  // senderKeyHex, recipientKeyHex?, content, timestamp
    var onLeave: ((String) -> Void)?                          // peerKeyHex
    var onPaymentRequest: ((String, UInt64, String, String) -> Void)?  // senderHex, amount, invoice, description
    var onPaymentNotification: ((Data, UInt64, UInt8) -> Void)?        // hash, amount, direction

    private init() {}

    // MARK: - Process Incoming Data

    func receive(data: Data, fromTransport: TransportType) {
        let msgID = computeMessageID(data)
        guard !isDuplicate(msgID) else { return }

        guard let packet = CrowdSyncBinaryProtocol.decode(data) else { return }
        routePacket(packet)

        // Relay if TTL > 1
        if packet.ttl > 1 {
            let relayed = CrowdSyncPacket(
                type: packet.type, senderID: packet.senderID,
                recipientID: packet.recipientID, timestamp: packet.timestamp,
                payload: packet.payload, signature: packet.signature,
                ttl: packet.ttl - 1, version: packet.version
            )
            relay(relayed, excludingTransport: fromTransport)
        }
    }

    // MARK: - Route by Type

    private func routePacket(_ packet: CrowdSyncPacket) {
        let senderHex = packet.senderID.map { String(format: "%02x", $0) }.joined()
        let timestamp = Date(timeIntervalSince1970: Double(packet.timestamp) / 1000.0)

        guard let type = MessageType(rawValue: packet.type) else { return }

        switch type {
        case .announce:
            let nickname = String(data: packet.payload, encoding: .utf8) ?? "Peer \(senderHex.prefix(4).uppercased())"
            onAnnounce?(senderHex, nickname, timestamp)

        case .message:
            let content = String(data: packet.payload, encoding: .utf8) ?? ""
            let recipientHex = packet.recipientID.map { $0.map { String(format: "%02x", $0) }.joined() } ?? ""
            onMessage?(senderHex, recipientHex, content, timestamp)

        case .leave:
            onLeave?(senderHex)

        case .paymentRequest:
            if let decoded = PaymentPacketSerializer.decodePaymentRequest(packet.payload) {
                onPaymentRequest?(senderHex, decoded.amountSat, decoded.invoice, decoded.description)
            }

        case .paymentNotification:
            if let decoded = PaymentPacketSerializer.decodePaymentNotification(packet.payload) {
                onPaymentNotification?(decoded.paymentHash, decoded.amountSat, decoded.direction)
            }

        case .noiseHandshake, .noiseEncrypted, .fragment, .fileTransfer, .requestSync:
            break // Handled by specialized subsystems
        }
    }

    // MARK: - Relay to Other Transports

    private func relay(_ packet: CrowdSyncPacket, excludingTransport: TransportType) {
        guard let data = CrowdSyncBinaryProtocol.encode(packet) else { return }
        if excludingTransport != .ble {
            BLEService.shared.broadcast(data)
        }
        if excludingTransport != .multipeer {
            MultipeerTransportManager.shared.send(data)
        }
    }

    // MARK: - Send Outgoing

    func sendMessage(content: String, senderID: Data, recipientID: Data? = nil) {
        let payload = content.data(using: .utf8) ?? Data()
        let packet = CrowdSyncPacket(
            type: MessageType.message.rawValue,
            senderID: senderID,
            recipientID: recipientID,
            payload: payload,
            ttl: 7
        )
        guard let data = CrowdSyncBinaryProtocol.encode(packet) else { return }
        BLEService.shared.broadcast(data)
        MultipeerTransportManager.shared.send(data)
    }

    func sendPaymentRequest(invoice: String, amountSat: UInt64, description: String, senderID: Data, recipientID: Data) {
        let payload = PaymentPacketSerializer.encodePaymentRequest(invoice: invoice, amountSat: amountSat, description: description)
        let packet = CrowdSyncPacket(
            type: MessageType.paymentRequest.rawValue,
            senderID: senderID,
            recipientID: recipientID,
            payload: payload,
            ttl: 7
        )
        guard let data = CrowdSyncBinaryProtocol.encode(packet) else { return }
        BLEService.shared.broadcast(data)
        MultipeerTransportManager.shared.send(data)
    }

    // MARK: - Deduplication

    private func computeMessageID(_ data: Data) -> String {
        SHA256.hash(data: data).prefix(16).map { String(format: "%02x", $0) }.joined()
    }

    private func isDuplicate(_ id: String) -> Bool {
        if let seen = seenIDs[id], Date().timeIntervalSince(seen) < dedupWindow {
            return true
        }
        seenIDs[id] = Date()
        if seenIDs.count > maxCacheSize {
            let cutoff = Date().addingTimeInterval(-dedupWindow)
            seenIDs = seenIDs.filter { $0.value > cutoff }
        }
        return false
    }
}

// MARK: - Transport Type

enum TransportType {
    case ble
    case multipeer
    case nostr
}
