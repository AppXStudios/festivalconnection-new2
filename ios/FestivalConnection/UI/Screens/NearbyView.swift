import SwiftUI

struct NearbyView: View {
    @EnvironmentObject var appState: AppState

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                VStack(alignment: .leading, spacing: 0) {
                    Text("Nearby")
                        .font(.system(size: 34, weight: .heavy))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.top, 16)
                        .padding(.bottom, 12)

                    // Section header
                    HStack {
                        Text("CROWDSYNC\u{2122}")
                            .font(.system(size: 13, weight: .semibold))
                            .tracking(1)
                            .foregroundStyle(FestivalTheme.mainGradient)

                        Circle()
                            .fill(appState.relayConnected ? FestivalTheme.presenceGreen : Color.red)
                            .frame(width: 8, height: 8)

                        Spacer()

                        Text("\(appState.nearbyFeed.count)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(FestivalTheme.accentPink)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)

                    if appState.nearbyFeed.isEmpty {
                        Spacer()
                        VStack(spacing: 12) {
                            Image(systemName: "antenna.radiowaves.left.and.right")
                                .font(.system(size: 72))
                                .foregroundColor(FestivalTheme.textSecondary)
                            Text("No one nearby")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(.white)
                            Text("CrowdSync\u{2122} is searching for festival attendees")
                                .font(.system(size: 15))
                                .foregroundColor(FestivalTheme.textSecondary)
                                .multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity)
                        Spacer()
                    } else {
                        ScrollViewReader { proxy in
                            ScrollView {
                                LazyVStack(spacing: 0) {
                                    ForEach(appState.nearbyFeed.reversed()) { msg in
                                        feedRow(msg)
                                            .id(msg.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .toolbar(.hidden, for: .navigationBar)
            .onAppear {
                appState.startNostrRelay()
            }
        }
    }

    private func feedRow(_ msg: ChannelMessage) -> some View {
        HStack(spacing: 12) {
            CircularAvatarView(
                displayName: msg.senderDisplayName,
                size: 40
            )

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(msg.senderDisplayName)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                    Text(msg.timestamp, style: .relative)
                        .font(.system(size: 12))
                        .foregroundColor(FestivalTheme.textMuted)
                }

                Text(msg.content)
                    .font(.system(size: 15))
                    .foregroundColor(FestivalTheme.textSecondary)
                    .lineLimit(2)

                if !msg.channelId.isEmpty {
                    HStack(spacing: 4) {
                        Image(systemName: "number")
                            .font(.system(size: 10))
                        Text(msg.channelId.prefix(8) + "...")
                            .font(.system(size: 11))
                    }
                    .foregroundColor(FestivalTheme.textMuted)
                }
            }

            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(FestivalTheme.backgroundBlack)
    }
}
