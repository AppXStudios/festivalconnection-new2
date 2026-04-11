import Foundation

// MARK: - CrowdSync Packet
// Binary packet structure copied from BitChat reference for cross-platform compatibility.
// Wire format matches BitChat's BinaryProtocol exactly.

struct CrowdSyncPacket: Codable {
    let version: UInt8
    let type: UInt8
    let senderID: Data       // 8 bytes
    let recipientID: Data?   // 8 bytes (optional, flag bit 0)
    let timestamp: UInt64    // milliseconds since epoch
    let payload: Data
    var signature: Data?     // 64 bytes (optional, flag bit 1)
    var ttl: UInt8

    init(type: UInt8, senderID: Data, recipientID: Data? = nil,
         timestamp: UInt64 = UInt64(Date().timeIntervalSince1970 * 1000),
         payload: Data, signature: Data? = nil, ttl: UInt8 = 7,
         version: UInt8 = 1) {
        self.version = version
        self.type = type
        self.senderID = senderID
        self.recipientID = recipientID
        self.timestamp = timestamp
        self.payload = payload
        self.signature = signature
        self.ttl = ttl
    }
}

// MARK: - Binary Protocol Encoding/Decoding
// Header structure (v1, 14 bytes):
//   [version:1][type:1][ttl:1][timestamp:8][flags:1][payloadLength:2]
// Then: [senderID:8][recipientID?:8][payload:var][signature?:64]

struct CrowdSyncBinaryProtocol {
    static let v1HeaderSize = 14
    static let senderIDSize = 8
    static let recipientIDSize = 8
    static let signatureSize = 64

    struct Flags {
        static let hasRecipient: UInt8  = 0x01
        static let hasSignature: UInt8  = 0x02
        static let isCompressed: UInt8  = 0x04
    }

    static func encode(_ packet: CrowdSyncPacket) -> Data? {
        var data = Data()
        data.reserveCapacity(v1HeaderSize + senderIDSize + packet.payload.count + 100)

        data.append(packet.version)
        data.append(packet.type)
        data.append(packet.ttl)

        // Timestamp: 8 bytes big-endian
        for shift in stride(from: 56, through: 0, by: -8) {
            data.append(UInt8((packet.timestamp >> UInt64(shift)) & 0xFF))
        }

        // Flags
        var flags: UInt8 = 0
        if packet.recipientID != nil { flags |= Flags.hasRecipient }
        if packet.signature != nil  { flags |= Flags.hasSignature }
        data.append(flags)

        // Payload length: 2 bytes big-endian (v1)
        let payloadLength = UInt16(min(packet.payload.count, Int(UInt16.max)))
        data.append(UInt8((payloadLength >> 8) & 0xFF))
        data.append(UInt8(payloadLength & 0xFF))

        // Sender ID (8 bytes, zero-padded)
        let senderBytes = packet.senderID.prefix(senderIDSize)
        data.append(senderBytes)
        if senderBytes.count < senderIDSize {
            data.append(Data(repeating: 0, count: senderIDSize - senderBytes.count))
        }

        // Optional recipient ID
        if let recipientID = packet.recipientID {
            let recipientBytes = recipientID.prefix(recipientIDSize)
            data.append(recipientBytes)
            if recipientBytes.count < recipientIDSize {
                data.append(Data(repeating: 0, count: recipientIDSize - recipientBytes.count))
            }
        }

        // Payload
        data.append(packet.payload)

        // Optional signature
        if let signature = packet.signature {
            data.append(signature.prefix(signatureSize))
        }

        return data
    }

    static func decode(_ data: Data) -> CrowdSyncPacket? {
        guard data.count >= v1HeaderSize + senderIDSize else { return nil }

        var offset = 0
        func read8() -> UInt8? {
            guard offset < data.count else { return nil }
            let v = data[offset]; offset += 1; return v
        }
        func readData(_ n: Int) -> Data? {
            guard offset + n <= data.count else { return nil }
            let d = data[offset..<(offset + n)]; offset += n; return Data(d)
        }

        guard let version = read8(), version == 1 else { return nil }
        guard let type = read8(), let ttl = read8() else { return nil }

        var timestamp: UInt64 = 0
        for _ in 0..<8 {
            guard let byte = read8() else { return nil }
            timestamp = (timestamp << 8) | UInt64(byte)
        }

        guard let flags = read8() else { return nil }
        let hasRecipient = (flags & Flags.hasRecipient) != 0
        let hasSignature = (flags & Flags.hasSignature) != 0

        guard let lenHi = read8(), let lenLo = read8() else { return nil }
        let payloadLength = Int((UInt16(lenHi) << 8) | UInt16(lenLo))

        guard let senderID = readData(senderIDSize) else { return nil }

        var recipientID: Data?
        if hasRecipient {
            recipientID = readData(recipientIDSize)
            if recipientID == nil { return nil }
        }

        guard let payload = readData(payloadLength) else { return nil }

        var signature: Data?
        if hasSignature {
            signature = readData(signatureSize)
            if signature == nil { return nil }
        }

        return CrowdSyncPacket(
            type: type, senderID: senderID, recipientID: recipientID,
            timestamp: timestamp, payload: payload, signature: signature,
            ttl: ttl, version: version
        )
    }
}
