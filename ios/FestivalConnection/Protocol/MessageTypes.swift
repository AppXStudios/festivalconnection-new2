import Foundation

// MARK: - Message Types
// Byte values copied verbatim from BitChat reference for cross-platform compatibility.

enum MessageType: UInt8 {
    // Public messages (unencrypted)
    case announce       = 0x01  // "I'm here" with nickname
    case message        = 0x02  // Public chat message
    case leave          = 0x03  // "I'm leaving"
    case requestSync    = 0x21  // GCS filter-based sync request

    // Noise encryption
    case noiseHandshake = 0x10  // Handshake (init or response)
    case noiseEncrypted = 0x11  // All encrypted payloads

    // Fragmentation
    case fragment       = 0x20  // Fragment for large messages
    case fileTransfer   = 0x22  // Binary file/audio/image payloads

    // Payment (Festival Connection extensions — does not conflict with BitChat)
    case paymentRequest      = 0x30  // Lightning invoice request
    case paymentNotification = 0x31  // Payment confirmation

    var description: String {
        switch self {
        case .announce:             return "announce"
        case .message:              return "message"
        case .leave:                return "leave"
        case .requestSync:          return "requestSync"
        case .noiseHandshake:       return "noiseHandshake"
        case .noiseEncrypted:       return "noiseEncrypted"
        case .fragment:             return "fragment"
        case .fileTransfer:         return "fileTransfer"
        case .paymentRequest:       return "paymentRequest"
        case .paymentNotification:  return "paymentNotification"
        }
    }
}

// MARK: - Noise Payload Types (inside noiseEncrypted messages)

enum NoisePayloadType: UInt8 {
    case privateMessage  = 0x01
    case readReceipt     = 0x02
    case delivered       = 0x03
    case verifyChallenge = 0x10
    case verifyResponse  = 0x11
}

// MARK: - Delivery Status

enum DeliveryStatus: Codable, Equatable, Hashable {
    case sending
    case sent
    case delivered(to: String, at: Date)
    case read(by: String, at: Date)
    case failed(reason: String)
}

// MARK: - Payment Serialization

struct PaymentPacketSerializer {
    /// Serialize a payment request: [amountSat:8][invoice:UTF8][0x00][description:UTF8]
    static func encodePaymentRequest(invoice: String, amountSat: UInt64, description: String) -> Data {
        var data = Data()
        // Amount as 8 bytes big-endian
        for shift in stride(from: 56, through: 0, by: -8) {
            data.append(UInt8((amountSat >> UInt64(shift)) & 0xFF))
        }
        // Invoice as UTF-8
        data.append(contentsOf: invoice.utf8)
        // Null delimiter
        data.append(0x00)
        // Description as UTF-8
        data.append(contentsOf: description.utf8)
        return data
    }

    /// Deserialize a payment request
    static func decodePaymentRequest(_ data: Data) -> (amountSat: UInt64, invoice: String, description: String)? {
        guard data.count >= 9 else { return nil }  // 8 bytes amount + at least 1 byte
        var amountSat: UInt64 = 0
        for i in 0..<8 {
            amountSat = (amountSat << 8) | UInt64(data[i])
        }
        let remaining = data.dropFirst(8)
        guard let nullIndex = remaining.firstIndex(of: 0x00) else { return nil }
        let invoiceData = remaining[remaining.startIndex..<nullIndex]
        let descData = remaining[(nullIndex + 1)...]
        let invoice = String(data: invoiceData, encoding: .utf8) ?? ""
        let description = String(data: Data(descData), encoding: .utf8) ?? ""
        return (amountSat, invoice, description)
    }

    /// Serialize a payment notification: [paymentHash:32][amountSat:8][direction:1]
    static func encodePaymentNotification(paymentHash: Data, amountSat: UInt64, direction: UInt8) -> Data {
        var data = Data()
        data.append(paymentHash.prefix(32))
        if paymentHash.count < 32 {
            data.append(Data(repeating: 0, count: 32 - paymentHash.count))
        }
        for shift in stride(from: 56, through: 0, by: -8) {
            data.append(UInt8((amountSat >> UInt64(shift)) & 0xFF))
        }
        data.append(direction)
        return data
    }

    /// Deserialize a payment notification
    static func decodePaymentNotification(_ data: Data) -> (paymentHash: Data, amountSat: UInt64, direction: UInt8)? {
        guard data.count >= 41 else { return nil }  // 32 + 8 + 1
        let hash = data.prefix(32)
        var amountSat: UInt64 = 0
        for i in 32..<40 {
            amountSat = (amountSat << 8) | UInt64(data[i])
        }
        let direction = data[40]
        return (Data(hash), amountSat, direction)
    }
}
