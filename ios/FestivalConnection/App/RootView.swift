import SwiftUI
import MultipeerConnectivity

struct RootView: View {
    @EnvironmentObject var permissionsManager: PermissionsManager
    @EnvironmentObject var identityManager: IdentityManager
    @EnvironmentObject var appState: AppState
    @AppStorage("fc_onboarding_complete") private var onboardingComplete = false
    @State private var showLaunch = true

    var body: some View {
        ZStack {
            FestivalTheme.backgroundBlack.ignoresSafeArea()

            if showLaunch {
                LaunchScreen()
                    .transition(.opacity)
            } else if onboardingComplete {
                MainTabView()
                    .onAppear {
                        startCrowdSyncTransports()
                    }
            } else if !identityManager.isInitialized {
                SettingUpView()
            } else {
                PermissionsView(onGetStarted: {
                    onboardingComplete = true
                })
            }
        }
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                withAnimation(.easeOut(duration: 0.5)) {
                    showLaunch = false
                }
            }
        }
    }

    // MARK: - CrowdSync™ Transport Startup

    private func startCrowdSyncTransports() {
        // Ensure both identities are initialized before any transport setup runs.
        // On relaunch with onboardingComplete=true, SettingUpView is skipped, so
        // publicKeyHex would otherwise be empty. Both initialize methods are idempotent.
        Task {
            await identityManager.initialize()
            NostrIdentity.shared.initialize()
            await MainActor.run {
                startTransportsAfterIdentity()
            }
        }
    }

    private func startTransportsAfterIdentity() {
        // 1. Nostr relay transport
        NostrRelayManager.shared.connect()
        appState.startNostrRelay()

        // 2. Breez SDK Lightning wallet
        WalletManager.shared.connect()

        // 3. BLE mesh transport
        let hexPrefix = String(identityManager.publicKeyHex.prefix(16))
        var peerIDData = Data()
        var idx = hexPrefix.startIndex
        while idx < hexPrefix.endIndex {
            let next = hexPrefix.index(idx, offsetBy: 2, limitedBy: hexPrefix.endIndex) ?? hexPrefix.endIndex
            if let byte = UInt8(hexPrefix[idx..<next], radix: 16) {
                peerIDData.append(byte)
            }
            idx = next
        }
        BLEService.shared.configure(
            peerIDData: peerIDData,
            nickname: UserDefaults.standard.string(forKey: "fc_nickname") ?? identityManager.displayName
        )
        BLEService.shared.onPeerDiscovered = { peerHex, nickname in
            Task { @MainActor in
                let peer = PeerInfo(publicKeyHex: peerHex, displayName: nickname, handle: peerHex, lastSeen: Date())
                appState.updatePeer(peer)
            }
        }
        BLEService.shared.onPeerDisconnected = { peerHex in
            Task { @MainActor in appState.removePeer(peerHex) }
        }
        BLEService.shared.onPacketReceived = { data in
            PacketProcessor.shared.receive(data: data, fromTransport: .ble)
        }
        BLEService.shared.start()

        // 4. MultipeerConnectivity transport
        MultipeerTransportManager.shared.configure(
            displayName: UserDefaults.standard.string(forKey: "fc_nickname") ?? identityManager.displayName,
            identityFingerprint: identityManager.publicKeyHex
        )
        MultipeerTransportManager.shared.onDataReceived = { data, _ in
            PacketProcessor.shared.receive(data: data, fromTransport: .multipeer)
        }
        MultipeerTransportManager.shared.onPeerConnected = { peerID in
            Task { @MainActor in
                // MCPeerID.displayName is formatted as "<nickname>_<fingerprint-prefix-8>"
                let name = peerID.displayName
                let components = name.split(separator: "_")
                let fingerprint = components.last.map(String.init) ?? name
                let nickname = components.count > 1
                    ? components.dropLast().joined(separator: "_")
                    : name
                let peer = PeerInfo(publicKeyHex: fingerprint, displayName: nickname, handle: fingerprint, lastSeen: Date())
                appState.updatePeer(peer)
            }
        }
        MultipeerTransportManager.shared.onPeerDisconnected = { peerID in
            Task { @MainActor in
                let name = peerID.displayName
                let fingerprint = name.split(separator: "_").last.map(String.init) ?? name
                appState.removePeer(fingerprint)
            }
        }
        MultipeerTransportManager.shared.start()

        // 5. Wire PacketProcessor callbacks to AppState
        PacketProcessor.shared.onAnnounce = { peerHex, nickname, _ in
            Task { @MainActor in
                let peer = PeerInfo(publicKeyHex: peerHex, displayName: nickname, handle: peerHex, lastSeen: Date())
                appState.updatePeer(peer)
            }
        }
        PacketProcessor.shared.onMessage = { senderHex, _, content, timestamp in
            Task { @MainActor in
                let msg = ChatMessage(senderKey: senderHex, recipientKey: "", content: content, isIncoming: true)
                appState.addMessage(msg, forPeer: senderHex)
            }
        }
        PacketProcessor.shared.onLeave = { peerHex in
            Task { @MainActor in appState.removePeer(peerHex) }
        }
        PacketProcessor.shared.onPaymentRequest = { senderHex, amount, invoice, description in
            Task { @MainActor in
                var msg = ChatMessage(senderKey: senderHex, recipientKey: "", content: "", isIncoming: true, messageType: 0x30)
                msg.paymentAmount = amount
                msg.paymentInvoice = invoice
                msg.paymentDescription = description
                appState.addMessage(msg, forPeer: senderHex)
            }
        }
        PacketProcessor.shared.onPaymentNotification = { hash, amount, direction in
            Task { @MainActor in
                // Refresh wallet balance on incoming payment notification
                WalletManager.shared.refreshBalance()
                WalletManager.shared.refreshTransactions()
                // Add UI bubble per audit M5 — surface incoming payment as a chat notification.
                // We don't always know the peer key in the mesh case, so route by hash if possible;
                // fallback: append to the most recently active conversation.
                let senderHex = hash.prefix(8).map { String(format: "%02x", $0) }.joined()
                var msg = ChatMessage(
                    senderKey: senderHex,
                    recipientKey: NostrIdentity.shared.publicKeyHex,
                    content: "",
                    isIncoming: direction == 0,
                    messageType: 0x31
                )
                msg.paymentAmount = amount
                msg.paymentHash = hash
                msg.paymentDirection = direction
                appState.addMessage(msg, forPeer: senderHex)
            }
        }
    }

}
