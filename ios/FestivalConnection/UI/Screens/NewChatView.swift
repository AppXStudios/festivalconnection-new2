import SwiftUI

struct NewChatView: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) var dismiss
    @State private var searchText = ""
    @State private var pulseScale: CGFloat = 1.0

    var filteredPeers: [PeerInfo] {
        if searchText.isEmpty { return appState.connectedPeers }
        return appState.connectedPeers.filter { $0.displayName.localizedCaseInsensitiveContains(searchText) }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                VStack(spacing: 0) {
                    Text("New Chat")
                        .font(.system(size: 28, weight: .heavy))
                        .foregroundColor(.white)
                        .padding(.top, 20)

                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(FestivalTheme.textMuted)
                        TextField("Search", text: $searchText)
                            .foregroundColor(.white)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                    }
                    .padding(.vertical, 10)
                    .padding(.horizontal, 12)
                    .background(FestivalTheme.surfaceMedium)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal, 16)
                    .padding(.top, 16)

                    if filteredPeers.isEmpty {
                        Spacer()
                        VStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .stroke(FestivalTheme.mainGradient, lineWidth: 2)
                                    .frame(width: 80, height: 80)
                                    .scaleEffect(pulseScale)
                                    .opacity(2 - pulseScale)
                                Image(systemName: "antenna.radiowaves.left.and.right")
                                    .font(.system(size: 64))
                                    .foregroundColor(FestivalTheme.textSecondary)
                            }
                            Text("Searching for nearby people...")
                                .font(.system(size: 15))
                                .foregroundColor(FestivalTheme.textSecondary)
                        }
                        .onAppear {
                            withAnimation(.easeInOut(duration: 1.5).repeatForever(autoreverses: false)) {
                                pulseScale = 2.0
                            }
                        }
                        Spacer()
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 0) {
                                ForEach(filteredPeers) { peer in
                                    Button(action: {
                                        if appState.conversations[peer.publicKeyHex] == nil {
                                            appState.conversations[peer.publicKeyHex] = []
                                        }
                                        dismiss()
                                    }) {
                                        HStack(spacing: 12) {
                                            CircularAvatarView(displayName: peer.displayName, profileImageData: peer.profileImageData, size: 40)
                                            Text(peer.displayName)
                                                .font(.system(size: 16, weight: .semibold))
                                                .foregroundColor(.white)
                                            Spacer()
                                        }
                                        .padding(.horizontal, 16)
                                        .padding(.vertical, 10)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(FestivalTheme.accentPink)
                }
            }
        }
    }
}
