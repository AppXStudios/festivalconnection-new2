import Foundation
import CryptoKit

// MARK: - Gossip Sync Manager
// GCS (Golomb-Coded Set) bloom filter based sync for efficient message
// deduplication across mesh peers. Adapted from BitChat reference.

final class GossipSyncManager {
    static let shared = GossipSyncManager()

    private struct SeenMessage {
        let idHex: String
        let timestamp: Date
        let packet: CrowdSyncPacket
    }

    private var seenMessages: [String: SeenMessage] = [:]
    private let maxCapacity = 1000
    private let maxAgeSeconds: TimeInterval = 900 // 15 minutes
    private let syncIntervalSeconds: TimeInterval = 15.0
    private var lastSyncTime = Date.distantPast

    private init() {}

    // MARK: - Record Seen Messages

    func recordMessage(idHex: String, packet: CrowdSyncPacket) {
        seenMessages[idHex] = SeenMessage(idHex: idHex, timestamp: Date(), packet: packet)
        pruneStale()
    }

    // MARK: - Build GCS Filter

    func buildSyncFilter() -> Data {
        let now = Date()
        let freshIDs = seenMessages.values
            .filter { now.timeIntervalSince($0.timestamp) < maxAgeSeconds }
            .map { $0.idHex }

        guard !freshIDs.isEmpty else { return Data() }

        // Simple bloom-like filter: hash each ID and pack into a bitfield
        var filter = Data(count: min(freshIDs.count * 2, 400))
        for id in freshIDs {
            let hash = SHA256.hash(data: id.data(using: .utf8) ?? Data())
            let hashBytes = Array(hash)
            let idx = Int((UInt16(hashBytes[0]) << 8 | UInt16(hashBytes[1]))) % (filter.count * 8)
            filter[idx / 8] |= (1 << (idx % 8))
        }
        return filter
    }

    // MARK: - Process Sync Request

    func handleSyncRequest(filter: Data, fromPeer peerID: String) -> [CrowdSyncPacket] {
        let now = Date()
        var missing: [CrowdSyncPacket] = []

        for (idHex, seen) in seenMessages {
            guard now.timeIntervalSince(seen.timestamp) < maxAgeSeconds else { continue }

            // Check if this ID is NOT in the peer's filter
            let hash = SHA256.hash(data: idHex.data(using: .utf8) ?? Data())
            let hashBytes = Array(hash)
            let idx = Int((UInt16(hashBytes[0]) << 8 | UInt16(hashBytes[1]))) % (filter.count * 8)

            guard filter.count > idx / 8 else { continue }
            if (filter[idx / 8] & (1 << (idx % 8))) == 0 {
                missing.append(seen.packet)
            }
        }

        return missing
    }

    // MARK: - Periodic Sync

    func shouldSync() -> Bool {
        Date().timeIntervalSince(lastSyncTime) >= syncIntervalSeconds
    }

    func markSynced() {
        lastSyncTime = Date()
    }

    // MARK: - Maintenance

    private func pruneStale() {
        let cutoff = Date().addingTimeInterval(-maxAgeSeconds)
        seenMessages = seenMessages.filter { $0.value.timestamp > cutoff }

        // Cap size
        if seenMessages.count > maxCapacity {
            let sorted = seenMessages.sorted { $0.value.timestamp > $1.value.timestamp }
            let trimmed = sorted.prefix(maxCapacity).map { ($0.key, $0.value) }
            seenMessages = Dictionary(uniqueKeysWithValues: trimmed)
        }
    }
}
