import Foundation
import MultipeerConnectivity

// MARK: - MultipeerConnectivity Transport
// Apple peer-to-peer framework for iOS-to-iOS communication without internet.
// Runs in parallel with BLE — messages are deduplicated across transports.

final class MultipeerTransportManager: NSObject, ObservableObject {
    static let shared = MultipeerTransportManager()

    private let serviceType = "fc-mesh"

    @Published private(set) var connectedPeerCount = 0
    @Published private(set) var isRunning = false

    private var localPeerID: MCPeerID!
    private var session: MCSession!
    private var advertiser: MCNearbyServiceAdvertiser!
    private var browser: MCNearbyServiceBrowser!

    // Callbacks
    var onDataReceived: ((Data, MCPeerID) -> Void)?
    var onPeerConnected: ((MCPeerID) -> Void)?
    var onPeerDisconnected: ((MCPeerID) -> Void)?

    private override init() {
        super.init()
    }

    func configure(displayName: String, identityFingerprint: String) {
        localPeerID = makePeerID(displayName: displayName, fingerprint: String(identityFingerprint.prefix(8)))
        session = MCSession(peer: localPeerID, securityIdentity: nil, encryptionPreference: .required)
        session.delegate = self

        advertiser = MCNearbyServiceAdvertiser(peer: localPeerID, discoveryInfo: nil, serviceType: serviceType)
        advertiser.delegate = self

        browser = MCNearbyServiceBrowser(peer: localPeerID, serviceType: serviceType)
        browser.delegate = self
    }

    /// MCPeerID requires a displayName of 1...63 UTF-8 bytes. With multibyte characters
    /// (emoji etc.) in a nickname we can easily blow past 63 bytes and crash. Truncate
    /// safely so the combined "<name>_<fingerprint>" stays within the limit.
    private func makePeerID(displayName: String, fingerprint: String) -> MCPeerID {
        let separatorBytes = 1 // "_"
        let maxNameBytes = max(0, 63 - fingerprint.utf8.count - separatorBytes)
        var safeName = displayName
        while safeName.utf8.count > maxNameBytes {
            safeName = String(safeName.dropLast())
        }
        if safeName.isEmpty { safeName = "Peer" }
        let combined = "\(safeName)_\(fingerprint)"
        return MCPeerID(displayName: combined)
    }

    func start() {
        guard localPeerID != nil else { return }
        advertiser.startAdvertisingPeer()
        browser.startBrowsingForPeers()
        DispatchQueue.main.async { self.isRunning = true }
    }

    func stop() {
        advertiser?.stopAdvertisingPeer()
        browser?.stopBrowsingForPeers()
        session?.disconnect()
        DispatchQueue.main.async {
            self.isRunning = false
            self.connectedPeerCount = 0
        }
    }

    func send(_ data: Data) {
        guard !session.connectedPeers.isEmpty else { return }
        try? session.send(data, toPeers: session.connectedPeers, with: .reliable)
    }

    func send(_ data: Data, to peer: MCPeerID) {
        try? session.send(data, toPeers: [peer], with: .reliable)
    }
}

// MARK: - MCSessionDelegate

extension MultipeerTransportManager: MCSessionDelegate {
    func session(_ session: MCSession, peer peerID: MCPeerID, didChange state: MCSessionState) {
        DispatchQueue.main.async {
            self.connectedPeerCount = session.connectedPeers.count
        }

        switch state {
        case .connected:
            DispatchQueue.main.async { self.onPeerConnected?(peerID) }
        case .notConnected:
            DispatchQueue.main.async { self.onPeerDisconnected?(peerID) }
        case .connecting:
            break
        @unknown default:
            break
        }
    }

    func session(_ session: MCSession, didReceive data: Data, fromPeer peerID: MCPeerID) {
        DispatchQueue.main.async { self.onDataReceived?(data, peerID) }
    }

    func session(_ session: MCSession, didReceive stream: InputStream, withName: String, fromPeer: MCPeerID) {}
    func session(_ session: MCSession, didStartReceivingResourceWithName: String, fromPeer: MCPeerID, with: Progress) {}
    func session(_ session: MCSession, didFinishReceivingResourceWithName: String, fromPeer: MCPeerID, at: URL?, withError: Error?) {}
}

// MARK: - MCNearbyServiceAdvertiserDelegate

extension MultipeerTransportManager: MCNearbyServiceAdvertiserDelegate {
    func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didReceiveInvitationFromPeer peerID: MCPeerID,
                    withContext context: Data?, invitationHandler: @escaping (Bool, MCSession?) -> Void) {
        invitationHandler(true, session)
    }
}

// MARK: - MCNearbyServiceBrowserDelegate

extension MultipeerTransportManager: MCNearbyServiceBrowserDelegate {
    func browser(_ browser: MCNearbyServiceBrowser, foundPeer peerID: MCPeerID, withDiscoveryInfo info: [String: String]?) {
        browser.invitePeer(peerID, to: session, withContext: nil, timeout: 60)
    }

    func browser(_ browser: MCNearbyServiceBrowser, lostPeer peerID: MCPeerID) {}
}
