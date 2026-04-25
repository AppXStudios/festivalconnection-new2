import Foundation
import Combine

@MainActor
final class AppState: ObservableObject {
    static let shared = AppState()

    @Published var connectedPeers: [PeerInfo] = []
    @Published var conversations: [String: [ChatMessage]] = [:]
    @Published var channels: [String: ChannelInfo] = [:]
    @Published var channelMessages: [String: [ChannelMessage]] = [:]
    @Published var channelMemberships: Set<String> = []
    @Published var unreadCounts: [String: Int] = [:]
    @Published var isInitialized: Bool = false

    // MARK: - Nostr Relay State
    @Published var nearbyFeed: [ChannelMessage] = []
    @Published var relayConnected: Bool = false

    private var channelDiscoverySubId: String?
    private var nearbyFeedSubId: String?
    private var channelSubIds: [String: String] = [:] // channelId -> subscriptionId
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Nostr Relay Lifecycle

    private var relayStarted = false

    func startNostrRelay() {
        guard !relayStarted else { return }
        relayStarted = true

        let relay = NostrRelayManager.shared
        relay.onEvent = { [weak self] event in
            Task { @MainActor in
                self?.handleNostrEvent(event)
            }
        }
        relay.connect()

        // Subscribe to channel discovery (kind-40) from last 24h
        let discoveryFilter = NostrChannels.channelDiscoveryFilter(
            since: Int64(Date().timeIntervalSince1970) - 86400
        )
        channelDiscoverySubId = relay.subscribe(filter: discoveryFilter)

        // Subscribe to all channel messages (kind-42) for the nearby live feed.
        // Per audit M2: we deliberately do NOT filter by `authors` here because that
        // would prevent discovery of new peers in the nearby feed (peers are populated
        // dynamically). Spam/JSON filtering happens in handleNostrEvent at the kind-42
        // handler level (see content checks below).
        var feedFilter = NostrFilter()
        feedFilter.kinds = [42]
        feedFilter.since = Int64(Date().timeIntervalSince1970) - 3600
        feedFilter.limit = 50
        nearbyFeedSubId = relay.subscribe(filter: feedFilter)

        // Subscribe to encrypted DMs (kind-4) addressed to us
        var dmFilter = NostrFilter()
        dmFilter.kinds = [4]
        dmFilter.since = Int64(Date().timeIntervalSince1970) - 86400
        dmFilter.pTags = [NostrIdentity.shared.publicKeyHex]
        _ = relay.subscribe(filter: dmFilter)

        // Subscribe to profile metadata (kind-0) for peers we see
        var metadataFilter = NostrFilter()
        metadataFilter.kinds = [0]
        metadataFilter.since = Int64(Date().timeIntervalSince1970) - 86400
        _ = relay.subscribe(filter: metadataFilter)

        // Track relay connection via Combine binding to NostrRelayManager
        NostrRelayManager.shared.$connectedRelayCount
            .receive(on: DispatchQueue.main)
            .sink { [weak self] count in
                self?.relayConnected = count > 0
            }
            .store(in: &cancellables)
    }

    func subscribeToChannelMessages(_ channelId: String) {
        guard channelSubIds[channelId] == nil else { return }
        let filter = NostrChannels.channelMessageFilter(
            channelId: channelId,
            since: Int64(Date().timeIntervalSince1970) - 86400
        )
        channelSubIds[channelId] = NostrRelayManager.shared.subscribe(filter: filter)
    }

    func unsubscribeFromChannelMessages(_ channelId: String) {
        if let subId = channelSubIds.removeValue(forKey: channelId) {
            NostrRelayManager.shared.unsubscribe(subId)
        }
    }

    func handleNostrEvent(_ event: NostrEvent) {
        switch event.kind {
        case 0:
            // Kind 0: Profile metadata
            if let data = event.content.data(using: .utf8),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let name = json["name"] as? String ?? json["display_name"] as? String ?? ""
                if !name.isEmpty {
                    let peer = PeerInfo(
                        publicKeyHex: event.pubkey,
                        displayName: name,
                        handle: json["nip05"] as? String ?? "",
                        lastSeen: Date()
                    )
                    updatePeer(peer)
                }
            }

        case 4:
            // Skip our own outgoing DMs echoed back by the relay — prevents phantom
            // self-conversations appearing in the chats list.
            if event.pubkey == NostrIdentity.shared.publicKeyHex { return }

            // Kind 4: Encrypted DM
            if let decrypted = NostrDM.decrypt(encryptedContent: event.content, senderPubkeyHex: event.pubkey) {
                // Try to parse as a structured payment payload first.
                if let jsonData = decrypted.data(using: .utf8),
                   let json = try? JSONSerialization.jsonObject(with: jsonData) as? [String: Any],
                   let type = json["type"] as? String,
                   type == "payment_request" {
                    let invoice = json["invoice"] as? String ?? ""
                    let amount = (json["amount"] as? UInt64)
                        ?? UInt64(json["amount"] as? Int ?? 0)
                    let descText = json["description"] as? String ?? ""
                    var msg = ChatMessage(
                        senderKey: event.pubkey,
                        recipientKey: NostrIdentity.shared.publicKeyHex,
                        content: "Payment Request",
                        isIncoming: true,
                        messageType: 0x30
                    )
                    msg.paymentAmount = amount
                    msg.paymentInvoice = invoice
                    msg.paymentDescription = descText.isEmpty ? nil : descText
                    addMessage(msg, forPeer: event.pubkey)
                } else {
                    let msg = ChatMessage(
                        senderKey: event.pubkey,
                        recipientKey: NostrIdentity.shared.publicKeyHex,
                        content: decrypted,
                        isIncoming: true
                    )
                    addMessage(msg, forPeer: event.pubkey)
                }
            }

        case 40:
            // Channel creation event (NIP-28). Per audit L4, don't drop legitimate
            // channels that happen to have an empty name — fall back to a placeholder.
            guard let meta = NostrChannels.parseChannelCreation(event) else { return }
            let displayName = meta.name.isEmpty ? "Untitled Channel" : meta.name
            var channel = ChannelInfo(id: event.id, name: displayName)
            channel.creatorPublicKeyHex = event.pubkey
            channel.creatorDisplayName = "Peer \(String(event.pubkey.prefix(8)))"
            channel.channelDescription = meta.about
            channel.createdAt = Date(timeIntervalSince1970: TimeInterval(event.createdAt))
            channel.memberPublicKeys = [event.pubkey]
            channel.memberAvatarNames = [channel.creatorDisplayName]
            addChannel(channel)

        case 42:
            // Channel message event (NIP-28)
            let channelId = event.tags.first(where: { $0.count >= 4 && $0[3] == "root" }).map { $0[1] } ?? ""
            // Prefer the live display name from any kind-0 metadata we already received
            // for this peer; fall back to the short pubkey prefix.
            let senderName = self.connectedPeers.first(where: { $0.publicKeyHex == event.pubkey })?.displayName
                ?? "Peer \(String(event.pubkey.prefix(8)))"
            let msg = ChannelMessage(
                id: event.id,
                channelId: channelId,
                senderPublicKeyHex: event.pubkey,
                senderDisplayName: senderName,
                content: event.content,
                timestamp: Date(timeIntervalSince1970: TimeInterval(event.createdAt))
            )

            // Add to specific channel messages if we have that channel
            if !channelId.isEmpty {
                addChannelMessage(msg)
                addChannelMember(publicKeyHex: event.pubkey, displayName: senderName, toChannelId: channelId)
            }

            // Add to nearby live feed — filter out raw JSON and non-text content
            let trimmed = event.content.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty,
               !trimmed.hasPrefix("{"),
               !trimmed.hasPrefix("["),
               trimmed.count >= 2,
               trimmed.count <= 500 {
                nearbyFeed.append(msg)
            }
            if nearbyFeed.count > 200 {
                nearbyFeed.removeFirst(nearbyFeed.count - 200)
            }

        default:
            break
        }
    }

    var sortedConversations: [(peerKey: String, messages: [ChatMessage])] {
        conversations
            .filter { !$0.value.isEmpty }
            .sorted { ($0.value.last?.timestamp ?? .distantPast) > ($1.value.last?.timestamp ?? .distantPast) }
            .map { (peerKey: $0.key, messages: $0.value) }
    }

    var sortedChannels: [ChannelInfo] {
        channels.values.sorted { ($0.lastMessageTimestamp ?? $0.createdAt) > ($1.lastMessageTimestamp ?? $1.createdAt) }
    }

    var totalChannelUnread: Int {
        channels.values.reduce(0) { $0 + $1.unreadCount }
    }

    // MARK: - DM Methods

    func addMessage(_ message: ChatMessage, forPeer peerKey: String) {
        if conversations[peerKey] == nil {
            conversations[peerKey] = []
        }
        conversations[peerKey]?.append(message)
        if message.isIncoming {
            unreadCounts[peerKey, default: 0] += 1
        }
    }

    func clearUnread(forPeer peerKey: String) {
        unreadCounts[peerKey] = 0
    }

    func updatePeer(_ peer: PeerInfo) {
        if let idx = connectedPeers.firstIndex(where: { $0.publicKeyHex == peer.publicKeyHex }) {
            connectedPeers[idx] = peer
        } else {
            connectedPeers.append(peer)
        }
    }

    func removePeer(_ publicKeyHex: String) {
        connectedPeers.removeAll { $0.publicKeyHex == publicKeyHex }
    }

    func peerName(for publicKeyHex: String) -> String {
        connectedPeers.first(where: { $0.publicKeyHex == publicKeyHex })?.displayName ?? "Peer \(String(publicKeyHex.prefix(4)).uppercased())"
    }

    func peerImageData(for publicKeyHex: String) -> Data? {
        connectedPeers.first(where: { $0.publicKeyHex == publicKeyHex })?.profileImageData
    }

    func setPaymentConfirmed(messageId: String, forPeer peerKey: String) {
        guard let idx = conversations[peerKey]?.firstIndex(where: { $0.id == messageId }) else { return }
        conversations[peerKey]?[idx].paymentConfirmed = true
    }

    // MARK: - Channel Methods

    @discardableResult
    func createChannel(name: String, description: String = "", isGeofenced: Bool = false) -> Bool {
        let myKey = IdentityManager.shared.publicKeyHex
        let myName = UserDefaults.standard.string(forKey: "fc_nickname") ?? IdentityManager.shared.displayName

        // Publish channel creation to Nostr relays (kind-40, NIP-28)
        let nostrEvent = NostrChannels.createChannel(name: name, about: description)
        let relayCount = NostrRelayManager.shared.publishEvent(nostrEvent)
        if relayCount == 0 {
            // No relays connected — surface the error, do NOT add ghost channel
            print("[AppState] createChannel: no relays connected, skipping local add")
            return false
        }

        // Use the Nostr event ID as the channel ID for relay-based lookup
        let channelId = nostrEvent.id

        var channel = ChannelInfo(id: channelId, name: name)
        channel.creatorPublicKeyHex = myKey
        channel.creatorDisplayName = myName
        channel.memberPublicKeys = [myKey]
        channel.memberAvatarNames = [myName]
        channel.channelDescription = description
        channel.isGeofenced = isGeofenced
        channel.createdAt = Date()
        channel.isJoined = true

        channels[channelId] = channel
        channelMemberships.insert(channelId)
        channelMessages[channelId] = []

        // Subscribe to messages for this channel
        subscribeToChannelMessages(channelId)
        return true
    }

    func addChannel(_ channel: ChannelInfo) {
        if channels[channel.id] == nil {
            channels[channel.id] = channel
        } else {
            channels[channel.id]?.name = channel.name
            channels[channel.id]?.channelDescription = channel.channelDescription
        }
    }

    func joinChannel(id: String) {
        let myKey = IdentityManager.shared.publicKeyHex
        let myName = UserDefaults.standard.string(forKey: "fc_nickname") ?? IdentityManager.shared.displayName
        channelMemberships.insert(id)
        channels[id]?.isJoined = true
        channels[id]?.memberPublicKeys.insert(myKey)
        if var names = channels[id]?.memberAvatarNames, names.count < 4 {
            names.append(myName)
            channels[id]?.memberAvatarNames = names
        }
    }

    func leaveChannel(id: String) {
        let myKey = IdentityManager.shared.publicKeyHex
        channelMemberships.remove(id)
        channels[id]?.isJoined = false
        channels[id]?.memberPublicKeys.remove(myKey)
    }

    func addChannelMessage(_ message: ChannelMessage) {
        let cid = message.channelId
        if channelMessages[cid] == nil {
            channelMessages[cid] = []
        }
        // Deduplicate
        guard !(channelMessages[cid]?.contains(where: { $0.id == message.id }) ?? false) else { return }
        channelMessages[cid]?.append(message)
        channels[cid]?.lastMessage = message.content
        channels[cid]?.lastMessageSenderName = message.senderDisplayName
        channels[cid]?.lastMessageTimestamp = message.timestamp
        if channelMemberships.contains(cid) && !message.isFromLocalUser {
            channels[cid]?.unreadCount += 1
        }
    }

    func clearChannelUnread(channelId: String) {
        channels[channelId]?.unreadCount = 0
    }

    func addChannelMember(publicKeyHex: String, displayName: String, toChannelId channelId: String) {
        channels[channelId]?.memberPublicKeys.insert(publicKeyHex)
        if var names = channels[channelId]?.memberAvatarNames, names.count < 4 {
            names.append(displayName)
            channels[channelId]?.memberAvatarNames = names
        }
    }

    func removeChannelMember(publicKeyHex: String, fromChannelId channelId: String) {
        channels[channelId]?.memberPublicKeys.remove(publicKeyHex)
    }

    func toggleChannelJoined(channelId: String) {
        if channelMemberships.contains(channelId) {
            leaveChannel(id: channelId)
        } else {
            joinChannel(id: channelId)
        }
    }
}
