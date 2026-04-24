import SwiftUI

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
        // 1. Nostr relay transport
        NostrRelayManager.shared.connect()
        appState.startNostrRelay()

        // 2. Breez SDK Lightning wallet
        WalletManager.shared.connect()

        // 3. BLE mesh transport
        let peerIDData = Data(identityManager.publicKeyHex.prefix(16).compactMap { c -> UInt8? in
            UInt8(String(c), radix: 16)
        })
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
    }

}
