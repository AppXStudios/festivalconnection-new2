import Foundation
import CryptoKit

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

    /// Serialize for ID computation per NIP-01
    static func serialize(pubkey: String, createdAt: Int64, kind: Int, tags: [[String]], content: String) -> String {
        let tagsJSON = tags.map { tag in
            "[" + tag.map { "\"\(escapeJSON($0))\"" }.joined(separator: ",") + "]"
        }
        let tagsStr = "[" + tagsJSON.joined(separator: ",") + "]"
        return "[0,\"\(pubkey)\",\(createdAt),\(kind),\(tagsStr),\"\(escapeJSON(content))\"]"
    }

    /// Compute event ID as SHA-256 of serialized event
    static func computeId(pubkey: String, createdAt: Int64, kind: Int, tags: [[String]], content: String) -> String {
        let serialized = serialize(pubkey: pubkey, createdAt: createdAt, kind: kind, tags: tags, content: content)
        let data = Data(serialized.utf8)
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

    /// Convert to JSON for sending
    func toJSON() -> String {
        let encoder = JSONEncoder()
        encoder.outputFormatting = []
        guard let data = try? encoder.encode(self) else { return "{}" }
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    private static func escapeJSON(_ str: String) -> String {
        var result = ""
        for char in str {
            switch char {
            case "\"": result += "\\\""
            case "\\": result += "\\\\"
            case "\n": result += "\\n"
            case "\r": result += "\\r"
            case "\t": result += "\\t"
            default: result.append(char)
            }
        }
        return result
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
