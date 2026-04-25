import SwiftUI
import StoreKit
import UIKit

struct SettingsView: View {
    @EnvironmentObject var appState: AppState
    @EnvironmentObject var identityManager: IdentityManager
    @ObservedObject private var wallet = WalletManager.shared
    @ObservedObject private var relayManager = NostrRelayManager.shared
    @State private var showEditProfile = false
    @State private var showShareSheet = false
    @State private var searchText = ""

    private var nickname: String {
        UserDefaults.standard.string(forKey: "fc_nickname") ?? identityManager.displayName
    }

    private var handle: String {
        UserDefaults.standard.string(forKey: "fc_handle") ?? identityManager.handle
    }

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                VStack(spacing: 0) {
                    // Non-scrolling header
                    Text("Settings")
                        .font(.system(size: 34, weight: .heavy))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)
                        .padding(.top, 16)
                        .padding(.bottom, 8)

                    // Search bar
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(FestivalTheme.textMuted)
                            .font(.system(size: 16))
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
                    .padding(.bottom, 16)

                    ScrollView {
                        VStack(spacing: 24) {
                            // Profile card
                            VStack(spacing: 0) {
                                Button(action: { showEditProfile = true }) {
                                    HStack(spacing: 16) {
                                        CircularAvatarView(displayName: nickname, profileImageData: UserDefaults.standard.data(forKey: "fc_profile_picture"), size: 60)
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(nickname)
                                                .font(.system(size: 17, weight: .semibold))
                                                .foregroundColor(.white)
                                            Text("@\(handle)")
                                                .font(.system(size: 15))
                                                .foregroundColor(FestivalTheme.textSecondary)
                                            if let about = UserDefaults.standard.string(forKey: "fc_about"), !about.isEmpty {
                                                Text(about)
                                                    .font(.system(size: 14))
                                                    .foregroundColor(FestivalTheme.textSecondary)
                                                    .padding(.top, 2)
                                            }
                                        }
                                        Spacer()
                                        Image(systemName: "qrcode")
                                            .font(.system(size: 22))
                                            .foregroundColor(.white)
                                    }
                                    .padding(16)
                                }

                                Divider().background(FestivalTheme.surfaceMedium)

                                settingsRow(icon: "person.badge.plus", label: "Invite a friend", iconColor: FestivalTheme.accentPink) {
                                    showShareSheet = true
                                }
                            }
                            .background(FestivalTheme.surfaceDark)
                            .clipShape(RoundedRectangle(cornerRadius: 16))

                            // Account section
                            sectionHeader("ACCOUNT")
                            VStack(spacing: 0) {
                                settingsNavRow(icon: "dollarsign.circle.fill", label: "Wallet", detail: String(format: "$%.2f", wallet.balanceUSD), iconColor: FestivalTheme.iconOrange) {
                                    WalletHomeView()
                                }
                                Divider().background(FestivalTheme.surfaceMedium)
                                settingsNavRow(icon: "globe", label: "Connection", detail: "\(appState.connectedPeers.count) nearby", iconColor: FestivalTheme.iconBlue) {
                                    NearbyView()
                                }
                                Divider().background(FestivalTheme.surfaceMedium)
                                settingsNavRow(icon: "hand.raised.fill", label: "Privacy", iconColor: FestivalTheme.iconPurple) {
                                    PrivacyInfoView()
                                }
                                Divider().background(FestivalTheme.surfaceMedium)
                                settingsNavRow(icon: "lock.shield.fill", label: "Security", iconColor: FestivalTheme.iconGreen) {
                                    SecurityInfoView()
                                }
                            }
                            .background(FestivalTheme.surfaceDark)
                            .clipShape(RoundedRectangle(cornerRadius: 16))

                            // Network section
                            sectionHeader("CONNECTIONS")
                            VStack(spacing: 0) {
                                settingsNavRow(icon: "antenna.radiowaves.left.and.right", label: "CrowdSync\u{2122}", detail: appState.relayConnected ? "Active" : "Searching", iconColor: FestivalTheme.accentPink) {
                                    NearbyView()
                                }
                                Divider().background(FestivalTheme.surfaceMedium)
                                settingsNavRow(icon: "arrow.triangle.2.circlepath", label: "Message Sync", detail: relayManager.connectedRelayCount > 0 ? "Connected" : "Offline", iconColor: FestivalTheme.iconBlue) {
                                    MessageSyncInfoView()
                                }
                            }
                            .background(FestivalTheme.surfaceDark)
                            .clipShape(RoundedRectangle(cornerRadius: 16))

                            // Preferences section
                            VStack(spacing: 0) {
                                settingsRow(icon: "bell.fill", label: "Notifications", iconColor: FestivalTheme.iconRed) {
                                    if let url = URL(string: UIApplication.openSettingsURLString) {
                                        UIApplication.shared.open(url)
                                    }
                                }
                                Divider().background(FestivalTheme.surfaceMedium)
                                settingsNavRow(icon: "internaldrive.fill", label: "Storage", iconColor: FestivalTheme.iconGray) {
                                    StorageInfoView()
                                }
                            }
                            .background(FestivalTheme.surfaceDark)
                            .clipShape(RoundedRectangle(cornerRadius: 16))

                            // About section
                            VStack(spacing: 0) {
                                settingsNavRow(icon: "info.circle.fill", label: "About Festival Connection", detail: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.7.0", iconColor: FestivalTheme.iconPurple) {
                                    AboutView()
                                }
                                Divider().background(FestivalTheme.surfaceMedium)
                                settingsRow(icon: "star.fill", label: "Rate App", iconColor: FestivalTheme.iconGold) {
                                    if let scene = UIApplication.shared.connectedScenes
                                        .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene {
                                        SKStoreReviewController.requestReview(in: scene)
                                    }
                                }
                            }
                            .background(FestivalTheme.surfaceDark)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 20)
                    }
                }
            }
            .sheet(isPresented: $showEditProfile) {
                EditProfileSheet()
            }
            .sheet(isPresented: $showShareSheet) {
                ShareSheet(items: ["Join me on Festival Connection! festivalconnection://invite"])
            }
        }
    }

    private func sectionHeader(_ title: String) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .tracking(1)
                .foregroundStyle(FestivalTheme.mainGradient)
            Spacer()
        }
    }

    private func settingsNavRow<D: View>(icon: String, label: String, detail: String? = nil, iconColor: Color = .white, @ViewBuilder destination: () -> D) -> some View {
        NavigationLink(destination: destination()) {
            HStack(spacing: 16) {
                GradientIcon(systemName: icon, size: 22)
                    .frame(width: 28, height: 28)
                Text(label)
                    .font(.system(size: 17))
                    .foregroundColor(.white)
                Spacer()
                if let detail = detail {
                    Text(detail)
                        .font(.system(size: 15))
                        .foregroundColor(FestivalTheme.textSecondary)
                }
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(FestivalTheme.textMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func settingsRow(icon: String, label: String, detail: String? = nil, iconColor: Color = .white, action: @escaping () -> Void = {}) -> some View {
        Button(action: {
            let impact = UIImpactFeedbackGenerator(style: .light)
            impact.impactOccurred()
            action()
        }) {
            HStack(spacing: 16) {
                GradientIcon(systemName: icon, size: 22)
                    .frame(width: 28, height: 28)

                Text(label)
                    .font(.system(size: 17))
                    .foregroundColor(.white)

                Spacer()

                if let detail = detail {
                    Text(detail)
                        .font(.system(size: 15))
                        .foregroundColor(FestivalTheme.textSecondary)
                }

                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(FestivalTheme.textMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Share Sheet

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

// MARK: - Info Views

private func infoCard(title: String, body: String) -> some View {
    VStack(alignment: .leading, spacing: 8) {
        Text(title)
            .font(.system(size: 17, weight: .semibold))
            .foregroundColor(.white)
        Text(body)
            .font(.system(size: 15))
            .foregroundColor(FestivalTheme.textSecondary)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(16)
    .background(FestivalTheme.surfaceDark)
    .clipShape(RoundedRectangle(cornerRadius: 16))
}

struct PrivacyInfoView: View {
    var body: some View {
        ZStack {
            FestivalTheme.backgroundBlack.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 16) {
                    Text("Privacy")
                        .font(.system(size: 34, weight: .heavy))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    infoCard(title: "Your Data Stays Yours",
                             body: "Festival Connection uses CrowdSync\u{2122} to deliver messages directly. Your conversations are never stored on our servers.")
                    infoCard(title: "Location Sharing",
                             body: "You choose when to share your location with friends. Location data is only sent when you tap the share button and is not tracked in the background.")
                }
                .padding(16)
            }
        }
    }
}

struct SecurityInfoView: View {
    var body: some View {
        ZStack {
            FestivalTheme.backgroundBlack.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 16) {
                    Text("Security")
                        .font(.system(size: 34, weight: .heavy))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    infoCard(title: "Encrypted Messages",
                             body: "All messages are encrypted before they leave your device, so only you and the people you are chatting with can read them.")
                    infoCard(title: "Identity Protection",
                             body: "Your unique identity key is generated on your device and never shared with anyone. You stay in control of who can contact you.")
                }
                .padding(16)
            }
        }
    }
}

struct MessageSyncInfoView: View {
    var body: some View {
        ZStack {
            FestivalTheme.backgroundBlack.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 16) {
                    Text("Message Sync")
                        .font(.system(size: 34, weight: .heavy))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    infoCard(title: "Stay Up to Date",
                             body: "Message Sync keeps your conversations current across nearby devices using the mesh network. Messages are delivered even when you have limited connectivity.")
                    infoCard(title: "How It Works",
                             body: "When you are near other Festival Connection users, messages are relayed through the crowd so everyone stays connected, even without cell service.")
                }
                .padding(16)
            }
        }
    }
}

struct StorageInfoView: View {
    var body: some View {
        ZStack {
            FestivalTheme.backgroundBlack.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 16) {
                    Text("Storage")
                        .font(.system(size: 34, weight: .heavy))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    infoCard(title: "Local Storage",
                             body: "Festival Connection stores messages and media on your device. Your data stays private and is not uploaded to any cloud service.")
                    infoCard(title: "Manage Space",
                             body: "Old messages and cached media can be cleared at any time from your device settings to free up storage space.")
                }
                .padding(16)
            }
        }
    }
}

struct AboutView: View {
    var body: some View {
        ZStack {
            FestivalTheme.backgroundBlack.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 16) {
                    Text("About")
                        .font(.system(size: 34, weight: .heavy))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    infoCard(title: "Festival Connection",
                             body: "Stay connected with friends at festivals, concerts, and events. Share moments, coordinate meetups, and never lose your crew in the crowd.")
                    infoCard(title: "Powered by CrowdSync\u{2122}",
                             body: "CrowdSync\u{2122} creates a mesh network between nearby devices so you can send messages, share locations, and stay in touch even without cell service.")
                }
                .padding(16)
            }
        }
    }
}
