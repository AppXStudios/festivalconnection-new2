import Foundation

struct ChannelMessage: Identifiable, Equatable {
    let id: String
    let channelId: String
    let senderPublicKeyHex: String
    let senderDisplayName: String
    let content: String
    let timestamp: Date

    init(id: String = UUID().uuidString, channelId: String, senderPublicKeyHex: String, senderDisplayName: String, content: String, timestamp: Date = Date()) {
        self.id = id
        self.channelId = channelId
        self.senderPublicKeyHex = senderPublicKeyHex
        self.senderDisplayName = senderDisplayName
        self.content = content
        self.timestamp = timestamp
    }

    @MainActor
    var isFromLocalUser: Bool {
        senderPublicKeyHex == IdentityManager.shared.publicKeyHex
    }
}
