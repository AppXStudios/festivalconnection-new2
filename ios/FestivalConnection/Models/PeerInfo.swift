import Foundation

struct PeerInfo: Identifiable, Equatable {
    var id: String { publicKeyHex }
    let publicKeyHex: String
    var displayName: String
    var handle: String
    var lastSeen: Date
    var profileImageData: Data?
    var isConnected: Bool = false
    var isReachable: Bool = false
    var noisePublicKey: Data?

    var connectionQuality: String {
        if isConnected { return "Nearby" }
        if isReachable { return "In range" }
        return "Searching..."
    }
}
