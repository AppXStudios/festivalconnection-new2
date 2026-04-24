import Foundation
import CryptoKit
import P256K

struct NostrEvent: Codable, Identifiable, Equatable {
    let id: String
    let pubkey: String
    let createdAt: Int64
    let kind: Int
    let tags: [[String]]
    let content: String
    let sig: String

    enum CodingKeys: String, CodingKey {
        case id, pubkey
        case createdAt = "created_at"
        case kind, tags, content, sig
    }

    static func == (lhs: NostrEvent, rhs: NostrEvent) -> Bool {
        lhs.id == rhs.id
    }

    /// Serialize for ID computation per NIP-01: [0, pubkey, created_at, kind, tags, content]
    /// Uses JSONSerialization with withoutEscapingSlashes for spec-compliant encoding,
    /// including \b, \f, \u00XX control characters, and proper UTF-8 handling.
    static func serializedData(pubkey: String, createdAt: Int64, kind: Int, tags: [[String]], content: String) -> Data {
        let serialized: [Any] = [
            0,
            pubkey,
            createdAt,
            kind,
            tags,
            content
        ]
        guard let data = try? JSONSerialization.data(
            withJSONObject: serialized,
            options: [.withoutEscapingSlashes]
        ) else {
            return Data()
        }
        return data
    }

    /// Compute event ID as SHA-256 hex of the canonically serialized event.
    static func computeId(pubkey: String, createdAt: Int64, kind: Int, tags: [[String]], content: String) -> String {
        let data = serializedData(pubkey: pubkey, createdAt: createdAt, kind: kind, tags: tags, content: content)
        let hash = SHA256.hash(data: data)
        return hash.compactMap { String(format: "%02x", $0) }.joined()
    }

    /// Create and sign a new event
    @MainActor
    static func create(kind: Int, content: String, tags: [[String]] = []) -> NostrEvent {
        let pubkey = NostrIdentity.shared.publicKeyHex
        let createdAt = Int64(Date().timeIntervalSince1970)
        let id = computeId(pubkey: pubkey, createdAt: createdAt, kind: kind, tags: tags, content: content)
        let idData = hexToData(id) ?? Data()
        let sigData = NostrIdentity.shared.sign(idData)
        let sig = sigData.map { String(format: "%02x", $0) }.joined()

        return NostrEvent(
            id: id,
            pubkey: pubkey,
            createdAt: createdAt,
            kind: kind,
            tags: tags,
            content: content,
            sig: sig
        )
    }

    /// Verify event ID matches content
    func verifyId() -> Bool {
        let computed = NostrEvent.computeId(pubkey: pubkey, createdAt: createdAt, kind: kind, tags: tags, content: content)
        return computed == id
    }

    /// Verify the BIP-340 Schnorr signature against the event ID and pubkey.
    /// Returns false if signature is missing, malformed, or does not validate.
    func verifySignature() -> Bool {
        guard let sigData = NostrEvent.hexToData(sig),
              sigData.count == 64,
              let pubData = NostrEvent.hexToData(pubkey),
              pubData.count == 32,
              let idData = NostrEvent.hexToData(id),
              idData.count == 32 else {
            return false
        }

        // The Nostr event ID IS the SHA-256 message hash that was Schnorr-signed.
        guard let signature = try? P256K.Schnorr.SchnorrSignature(dataRepresentation: sigData) else {
            return false
        }

        var messageBytes = [UInt8](idData)
        let xonly = P256K.Schnorr.XonlyKey(dataRepresentation: pubData)
        return xonly.isValid(signature, for: &messageBytes)
    }

    /// Convert to JSON for sending
    func toJSON() -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = []
        guard let data = try? encoder.encode(self) else { return "{}" }
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    private static func hexToData(_ hex: String) -> Data? {
        var data = Data()
        var temp = hex
        while temp.count >= 2 {
            let s = String(temp.prefix(2))
            temp = String(temp.dropFirst(2))
            guard let b = UInt8(s, radix: 16) else { return nil }
            data.append(b)
        }
        return data
    }
}
