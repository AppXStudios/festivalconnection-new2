import Foundation
import Combine

enum RelayStatus: String {
    case connecting, connected, disconnected, failed
}

@MainActor
final class NostrRelayManager: ObservableObject {
    static let shared = NostrRelayManager()

    @Published var connectedRelayCount: Int = 0
    @Published var relayStatuses: [String: RelayStatus] = [:]

    private var connections: [String: NostrRelayConnection] = [:]
    private var subscriptions: [String: [NostrFilter]] = [:]
    private var eventCache: Set<String> = []
    private var eventCacheTimestamps: [String: Date] = [:]
    private let cacheExpiry: TimeInterval = 3600 // 1 hour

    // Per-relay consecutive failure tracking. Mirrors bitchat's `isPermanentlyFailed`
    // pattern: after N failures we stop retrying that relay until reset/manual retry.
    private var relayFailureCounts: [String: Int] = [:]
    private let maxConsecutiveFailures = 10

    private let defaultRelays = [
        "wss://relay.damus.io",
        "wss://relay.nostr.band",
        "wss://nos.lol",
        "wss://relay.snort.social",
        "wss://nostr.wine"
    ]

    var onEvent: ((NostrEvent) -> Void)?

    // Georelays dataset
    private struct GeoRelay {
        let url: String
        let lat: Double
        let lon: Double
    }
    private var geoRelays: [GeoRelay] = []
    private let geoRelayConnectCount = 10

    func connect() {
        // Connect to default relays first
        for url in defaultRelays {
            connectToRelay(url)
        }
        // Load and connect to nearest geo relays
        loadGeoRelays()
    }

    func connectToNearestRelays(latitude: Double, longitude: Double) {
        guard !geoRelays.isEmpty else { return }
        let sorted = geoRelays.sorted { a, b in
            haversine(lat1: latitude, lon1: longitude, lat2: a.lat, lon2: a.lon) <
            haversine(lat1: latitude, lon1: longitude, lat2: b.lat, lon2: b.lon)
        }
        for relay in sorted.prefix(geoRelayConnectCount) {
            let url = relay.url.hasPrefix("wss://") ? relay.url : "wss://\(relay.url)"
            connectToRelay(url)
        }
    }

    private func loadGeoRelays() {
        // Try bundle CSV first, then app-specific georelays
        let csvURLs = [
            Bundle.main.url(forResource: "nostr_relays", withExtension: "csv"),
            Bundle.main.url(forResource: "georelays", withExtension: "csv")
        ].compactMap { $0 }

        guard let csvURL = csvURLs.first else { return }
        guard let content = try? String(contentsOf: csvURL, encoding: .utf8) else { return }

        let lines = content.components(separatedBy: .newlines)
        for line in lines.dropFirst() { // Skip header
            let parts = line.components(separatedBy: ",")
            guard parts.count >= 3,
                  let lat = Double(parts[1].trimmingCharacters(in: .whitespaces)),
                  let lon = Double(parts[2].trimmingCharacters(in: .whitespaces)) else { continue }
            let url = parts[0].trimmingCharacters(in: .whitespaces)
            guard !url.isEmpty else { continue }
            geoRelays.append(GeoRelay(url: url, lat: lat, lon: lon))
        }
    }

    // Haversine distance in kilometers
    private func haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let R = 6371.0
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat/2) * sin(dLat/2) +
                cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) *
                sin(dLon/2) * sin(dLon/2)
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    func disconnect() {
        for (_, conn) in connections {
            conn.disconnect()
        }
        connections.removeAll()
        relayStatuses.removeAll()
        connectedRelayCount = 0
        // A full disconnect resets the failure-tracking state so a subsequent connect()
        // gets a clean slate (parity with bitchat `resetAllConnections`).
        relayFailureCounts.removeAll()
    }

    /// Publish a Nostr event to all currently-connected relays.
    /// - Returns: Number of relays the event was sent to (0 if none connected).
    @discardableResult
    func publishEvent(_ event: NostrEvent) -> Int {
        let message = ClientMessage.event(event).serialized()
        var count = 0
        for (_, conn) in connections where conn.status == .connected {
            conn.send(message)
            count += 1
        }
        if count == 0 {
            print("[NostrRelay] publishEvent kind=\(event.kind) — WARNING: 0 relays connected")
        } else {
            print("[NostrRelay] publishEvent kind=\(event.kind) sent to \(count) relays")
        }
        return count
    }

    func subscribe(filter: NostrFilter) -> String {
        let subId = UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(16).lowercased()
        let subIdStr = String(subId)
        subscriptions[subIdStr] = [filter]

        let message = ClientMessage.req(subscriptionId: subIdStr, filters: [filter]).serialized()
        for (_, conn) in connections where conn.status == .connected {
            conn.send(message)
        }
        return subIdStr
    }

    func unsubscribe(_ subscriptionId: String) {
        subscriptions.removeValue(forKey: subscriptionId)
        let message = ClientMessage.close(subscriptionId: subscriptionId).serialized()
        for (_, conn) in connections where conn.status == .connected {
            conn.send(message)
        }
    }

    // MARK: - Private

    private func connectToRelay(_ urlString: String) {
        guard connections[urlString] == nil else { return }
        // Skip relays that have failed too many times in a row (parity with bitchat
        // `isPermanentlyFailed`). They are reset on disconnect()/resetAllConnections().
        if isPermanentlyFailed(urlString) {
            print("[NostrRelay] Skipping permanently-failed relay: \(urlString)")
            relayStatuses[urlString] = .failed
            return
        }

        let conn = NostrRelayConnection(urlString: urlString) { [weak self] message in
            Task { @MainActor in
                self?.handleRelayMessage(message, from: urlString)
            }
        } onStatusChange: { [weak self] status in
            Task { @MainActor in
                guard let self = self else { return }
                self.relayStatuses[urlString] = status
                self.connectedRelayCount = self.connections.values.filter { $0.status == .connected }.count

                if status == .connected {
                    // Reset failure count on successful connection.
                    self.relayFailureCounts[urlString] = 0
                    // Re-send all currently-active subscription handlers as REQ messages
                    // to this relay (NIP-01). Verified: this iterates the full
                    // `subscriptions` dict and sends each one. Mirrors bitchat's
                    // `flushPendingSubscriptions` pattern at a smaller scale.
                    self.resendSubscriptions(to: urlString)
                } else if status == .disconnected || status == .failed {
                    // Track consecutive failures; once we exceed the threshold the
                    // relay is treated as permanently failed and the underlying
                    // NostrRelayConnection's reconnect loop should be cancelled.
                    self.relayFailureCounts[urlString, default: 0] += 1
                    if self.isPermanentlyFailed(urlString) {
                        print("[NostrRelay] Relay \(urlString) marked permanently failed after \(self.relayFailureCounts[urlString] ?? 0) failures — cancelling reconnect loop")
                        if let existing = self.connections[urlString] {
                            existing.cancelReconnect()
                            self.connections.removeValue(forKey: urlString)
                        }
                        self.relayStatuses[urlString] = .failed
                    }
                }
            }
        }

        connections[urlString] = conn
        relayStatuses[urlString] = .connecting
        conn.connect()
    }

    /// Re-issue REQ messages for every active subscription to a relay that just
    /// (re)connected. Subscriptions are tracked globally (not per-relay) because we
    /// fan-out the same filter to every connected relay; on reconnect every one of
    /// them needs to be replayed.
    private func resendSubscriptions(to urlString: String) {
        guard let conn = connections[urlString] else { return }
        for (subId, filters) in subscriptions {
            let message = ClientMessage.req(subscriptionId: subId, filters: filters).serialized()
            conn.send(message)
        }
    }

    /// True once a relay has failed `maxConsecutiveFailures` times in a row without a
    /// successful connection in between. The count is reset on successful connect.
    private func isPermanentlyFailed(_ url: String) -> Bool {
        (relayFailureCounts[url] ?? 0) >= maxConsecutiveFailures
    }

    private func handleRelayMessage(_ json: String, from relay: String) {
        guard let message = RelayMessage.parse(json: json) else { return }

        switch message {
        case .event(_, let event):
            // Deduplicate
            guard !eventCache.contains(event.id) else { return }
            cleanExpiredCache()
            eventCache.insert(event.id)
            eventCacheTimestamps[event.id] = Date()

            // Verify ID + Schnorr signature (NIP-01)
            guard event.verifyId(), event.verifySignature() else { return }

            // Route by kind
            onEvent?(event)

        case .ok(let eventId, let accepted, let msg):
            if !accepted {
                print("[Nostr] Event \(eventId.prefix(8)) rejected: \(msg)")
            }

        case .eose(let subId):
            print("[Nostr] EOSE for subscription \(subId.prefix(8))")

        case .closed(let subId, let msg):
            print("[Nostr] Subscription \(subId.prefix(8)) closed: \(msg)")
            subscriptions.removeValue(forKey: subId)

        case .notice(let msg):
            print("[Nostr] NOTICE: \(msg)")
        }
    }

    private func cleanExpiredCache() {
        let now = Date()
        let expired = eventCacheTimestamps.filter { now.timeIntervalSince($0.value) > cacheExpiry }
        for (id, _) in expired {
            eventCache.remove(id)
            eventCacheTimestamps.removeValue(forKey: id)
        }
    }
}

// MARK: - Individual Relay Connection

final class NostrRelayConnection: NSObject, URLSessionWebSocketDelegate {
    let urlString: String
    private(set) var status: RelayStatus = .disconnected

    private var webSocketTask: URLSessionWebSocketTask?
    private var session: URLSession?
    private var reconnectDelay: TimeInterval = 2.0
    private let maxReconnectDelay: TimeInterval = 120.0
    private var shouldReconnect = true

    private let onMessage: (String) -> Void
    private let onStatusChange: (RelayStatus) -> Void

    init(urlString: String, onMessage: @escaping (String) -> Void, onStatusChange: @escaping (RelayStatus) -> Void) {
        self.urlString = urlString
        self.onMessage = onMessage
        self.onStatusChange = onStatusChange
        super.init()
        self.session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)
    }

    func connect() {
        guard let url = URL(string: urlString) else { return }
        shouldReconnect = true
        status = .connecting
        onStatusChange(.connecting)

        webSocketTask = session?.webSocketTask(with: url)
        webSocketTask?.resume()
        receiveMessage()
    }

    func disconnect() {
        shouldReconnect = false
        webSocketTask?.cancel(with: .goingAway, reason: nil)
        status = .disconnected
        onStatusChange(.disconnected)
    }

    /// Stop the reconnect loop for this socket without firing another status change.
    /// Used when the manager has classified this relay as permanently failed.
    func cancelReconnect() {
        shouldReconnect = false
        webSocketTask?.cancel(with: .goingAway, reason: nil)
        webSocketTask = nil
    }

    func send(_ message: String) {
        webSocketTask?.send(.string(message)) { error in
            if let error = error {
                print("[Nostr] Send error to \(self.urlString): \(error.localizedDescription)")
            }
        }
    }

    private func receiveMessage() {
        webSocketTask?.receive { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .success(let message):
                switch message {
                case .string(let text):
                    self.onMessage(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) {
                        self.onMessage(text)
                    }
                @unknown default: break
                }
                self.receiveMessage() // Continue listening

            case .failure:
                self.handleDisconnection()
            }
        }
    }

    private func handleDisconnection() {
        status = .disconnected
        onStatusChange(.disconnected)

        guard shouldReconnect else { return }

        let delay = reconnectDelay
        reconnectDelay = min(reconnectDelay * 2, maxReconnectDelay)

        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self] in
            self?.connect()
        }
    }

    // MARK: - URLSessionWebSocketDelegate

    nonisolated func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        Task { @MainActor in
            self.status = .connected
            self.reconnectDelay = 2.0
            self.onStatusChange(.connected)
        }
    }

    nonisolated func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        self.handleDisconnection()
    }
}
