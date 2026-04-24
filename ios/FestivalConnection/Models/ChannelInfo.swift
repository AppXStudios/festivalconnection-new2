import Foundation

struct ChannelInfo: Identifiable, Equatable {
    let id: String
    var name: String
    var creatorPublicKeyHex: String = ""
    var creatorDisplayName: String = ""
    var memberPublicKeys: Set<String> = []
    var memberAvatarNames: [String] = [] // up to 4 display names for stacked avatars
    var lastMessage: String = ""
    var lastMessageSenderName: String = ""
    var lastMessageTimestamp: Date? = nil
    var unreadCount: Int = 0
    var isGeofenced: Bool = false
    var channelDescription: String = ""
    var createdAt: Date = Date()
    var isVerified: Bool = false
    var isJoined: Bool = false

    var memberCount: Int { memberPublicKeys.count }

    static func == (lhs: ChannelInfo, rhs: ChannelInfo) -> Bool {
        lhs.id == rhs.id
    }
}
