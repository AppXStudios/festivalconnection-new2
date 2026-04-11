import Foundation

enum NostrChannels {
    /// Create a kind-40 channel creation event (NIP-28)
    @MainActor
    static func createChannel(name: String, about: String = "", picture: String = "") -> NostrEvent {
        let contentDict: [String: String] = [
            "name": name,
            "about": about,
            "picture": picture
        ]
        let contentJSON = (try? JSONSerialization.data(withJSONObject: contentDict))
            .flatMap { String(data: $0, encoding: .utf8) } ?? "{}"

        return NostrEvent.create(kind: 40, content: contentJSON)
    }

    /// Create a kind-42 channel message event (NIP-28)
    @MainActor
    static func sendChannelMessage(channelId: String, content: String, replyTo: String? = nil) -> NostrEvent {
        var tags: [[String]] = [["e", channelId, "", "root"]]

        if let replyId = replyTo {
            tags.append(["e", replyId, "", "reply"])
        }

        return NostrEvent.create(kind: 42, content: content, tags: tags)
    }

    /// Create a subscription filter for channel messages
    static func channelMessageFilter(channelId: String, since: Int64? = nil) -> NostrFilter {
        var filter = NostrFilter()
        filter.kinds = [42]
        filter.eTags = [channelId]
        if let since = since { filter.since = since }
        filter.limit = 100
        return filter
    }

    /// Create a subscription filter to discover new channels
    static func channelDiscoveryFilter(since: Int64? = nil) -> NostrFilter {
        var filter = NostrFilter()
        filter.kinds = [40]
        if let since = since { filter.since = since }
        filter.limit = 50
        return filter
    }

    /// Parse a kind-40 event into channel metadata
    static func parseChannelCreation(_ event: NostrEvent) -> (name: String, about: String, picture: String)? {
        guard event.kind == 40 else { return nil }
        guard let data = event.content.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return nil }

        let name = json["name"] as? String ?? ""
        let about = json["about"] as? String ?? ""
        let picture = json["picture"] as? String ?? ""

        return (name, about, picture)
    }
}
