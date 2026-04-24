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
    }

    func publishEvent(_ event: NostrEvent) {
        let message = ClientMessage.event(event).serialized()
        for (_, conn) in connections where conn.status == .connected {
            conn.send(message)
        }
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

        let conn = NostrRelayConnection(urlString: urlString) { [weak self] message in
            Task { @MainActor in
                self?.handleRelayMessage(message, from: urlString)
            }
        } onStatusChange: { [weak self] status in
            Task { @MainActor in
                self?.relayStatuses[urlString] = status
                self?.connectedRelayCount = self?.connections.values.filter { $0.status == .connected }.count ?? 0

                // Re-send subscriptions on reconnect
                if status == .connected {
                    self?.resendSubscriptions(to: urlString)
                }
            }
        }

        connections[urlString] = conn
        relayStatuses[urlString] = .connecting
        conn.connect()
    }

    private func resendSubscriptions(to urlString: String) {
        guard let conn = connections[urlString] else { return }
        for (subId, filters) in subscriptions {
            let message = ClientMessage.req(subscriptionId: subId, filters: filters).serialized()
            conn.send(message)
        }
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
