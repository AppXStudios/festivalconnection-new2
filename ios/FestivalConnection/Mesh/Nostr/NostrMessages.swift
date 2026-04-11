import Foundation

// MARK: - Client-to-Relay Messages

enum ClientMessage {
    case event(NostrEvent)
    case req(subscriptionId: String, filters: [NostrFilter])
    case close(subscriptionId: String)

    func serialized() -> String {
        switch self {
        case .event(let event):
            return "[\"EVENT\",\(event.toJSON())]"
        case .req(let subId, let filters):
            var parts = ["\"REQ\"", "\"\(subId)\""]
            for filter in filters {
                parts.append(filter.toJSON())
            }
            return "[\(parts.joined(separator: ","))]"
        case .close(let subId):
            return "[\"CLOSE\",\"\(subId)\"]"
        }
    }
}

// MARK: - Relay-to-Client Messages

enum RelayMessage {
    case event(subscriptionId: String, event: NostrEvent)
    case ok(eventId: String, accepted: Bool, message: String)
    case eose(subscriptionId: String)
    case closed(subscriptionId: String, message: String)
    case notice(message: String)

    static func parse(json: String) -> RelayMessage? {
        guard let data = json.data(using: .utf8),
              let array = try? JSONSerialization.jsonObject(with: data) as? [Any],
              let type = array.first as? String else { return nil }

        switch type {
        case "EVENT":
            guard array.count >= 3,
                  let subId = array[1] as? String,
                  let eventDict = array[2] as? [String: Any] else { return nil }
            guard let event = parseEvent(from: eventDict) else { return nil }
            return .event(subscriptionId: subId, event: event)

        case "OK":
            guard array.count >= 4,
                  let eventId = array[1] as? String,
                  let accepted = array[2] as? Bool else { return nil }
            let message = (array.count > 3 ? array[3] as? String : nil) ?? ""
            return .ok(eventId: eventId, accepted: accepted, message: message)

        case "EOSE":
            guard array.count >= 2, let subId = array[1] as? String else { return nil }
            return .eose(subscriptionId: subId)

        case "CLOSED":
            guard array.count >= 3, let subId = array[1] as? String else { return nil }
            let msg = (array[2] as? String) ?? ""
            return .closed(subscriptionId: subId, message: msg)

        case "NOTICE":
            guard array.count >= 2, let msg = array[1] as? String else { return nil }
            return .notice(message: msg)

        default:
            return nil
        }
    }

    private static func parseEvent(from dict: [String: Any]) -> NostrEvent? {
        guard let id = dict["id"] as? String,
              let pubkey = dict["pubkey"] as? String,
              let createdAt = dict["created_at"] as? Int64 ?? (dict["created_at"] as? Int).map({ Int64($0) }),
              let kind = dict["kind"] as? Int,
              let tags = dict["tags"] as? [[String]],
              let content = dict["content"] as? String,
              let sig = dict["sig"] as? String else { return nil }

        return NostrEvent(id: id, pubkey: pubkey, createdAt: createdAt, kind: kind, tags: tags, content: content, sig: sig)
    }
}

// MARK: - Filter

struct NostrFilter {
    var ids: [String]?
    var authors: [String]?
    var kinds: [Int]?
    var since: Int64?
    var until: Int64?
    var limit: Int?
    var eTags: [String]?
    var pTags: [String]?

    func toJSON() -> String {
        var dict: [String: Any] = [:]
        if let ids = ids { dict["ids"] = ids }
        if let authors = authors { dict["authors"] = authors }
        if let kinds = kinds { dict["kinds"] = kinds }
        if let since = since { dict["since"] = since }
        if let until = until { dict["until"] = until }
        if let limit = limit { dict["limit"] = limit }
        if let eTags = eTags { dict["#e"] = eTags }
        if let pTags = pTags { dict["#p"] = pTags }
        guard let data = try? JSONSerialization.data(withJSONObject: dict),
              let str = String(data: data, encoding: .utf8) else { return "{}" }
        return str
    }
}
