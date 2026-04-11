import SwiftUI

enum FestivalTab: String, CaseIterable {
    case chats, nearby, channels, wallet, qr, settings

    var icon: String {
        switch self {
        case .chats: return "bubble.left.and.bubble.right.fill"
        case .nearby: return "antenna.radiowaves.left.and.right"
        case .channels: return "number"
        case .wallet: return "dollarsign.circle.fill"
        case .qr: return "qrcode"
        case .settings: return "gearshape.fill"
        }
    }

    var label: String {
        switch self {
        case .chats: return "Chats"
        case .nearby: return "Nearby"
        case .channels: return "Channels"
        case .wallet: return "Wallet"
        case .qr: return "QR"
        case .settings: return "Settings"
        }
    }
}

struct MainTabView: View {
    @State private var selectedTab: FestivalTab = .chats

    var body: some View {
        ZStack(alignment: .bottom) {
            FestivalTheme.backgroundBlack.ignoresSafeArea()

            Group {
                switch selectedTab {
                case .chats: ChatsView()
                case .nearby: NearbyView()
                case .channels: ChannelsView()
                case .wallet: WalletHomeView()
                case .qr: QRView()
                case .settings: SettingsView()
                }
            }
            .padding(.bottom, 80)

            // Custom floating tab bar
            HStack(spacing: 0) {
                ForEach(FestivalTab.allCases, id: \.self) { tab in
                    Button(action: { selectedTab = tab }) {
                        VStack(spacing: 4) {
                            ZStack {
                                if selectedTab == tab {
                                    GradientIcon(systemName: tab.icon, size: 24)
                                } else {
                                    Image(systemName: tab.icon)
                                        .font(.system(size: 20))
                                        .foregroundColor(FestivalTheme.textMuted)
                                }
                            }
                            .frame(height: 40)

                            Text(tab.label)
                                .font(.system(size: 10))
                                .foregroundStyle(selectedTab == tab ? FestivalTheme.mainGradient : LinearGradient(colors: [FestivalTheme.textMuted], startPoint: .leading, endPoint: .trailing))
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .background(FestivalTheme.surfaceDark)
            .clipShape(RoundedRectangle(cornerRadius: 28))
            .shadow(color: .black.opacity(0.5), radius: 16)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
        }
    }
}
