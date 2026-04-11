import SwiftUI

struct ChannelsView: View {
    @EnvironmentObject var appState: AppState
    @State private var searchText = ""
    @State private var showCreateChannel = false
    @State private var showFilter = false
    @State private var showNotificationAlert = false

    var filteredChannels: [ChannelInfo] {
        let sorted = appState.sortedChannels
        if searchText.isEmpty { return sorted }
        return sorted.filter { $0.name.localizedCaseInsensitiveContains(searchText) }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                VStack(spacing: 0) {
                    // Header with search and action buttons
                    HStack(spacing: 10) {
                        HStack(spacing: 8) {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(FestivalTheme.textMuted)
                                .font(.system(size: 16))
                            TextField("Your Convos", text: $searchText)
                                .foregroundColor(.white)
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.never)
                        }
                        .padding(.vertical, 10)
                        .padding(.horizontal, 12)
                        .background(FestivalTheme.surfaceMedium)
                        .clipShape(RoundedRectangle(cornerRadius: 12))

                        Button(action: { showFilter = true }) {
                            GradientIcon(systemName: "line.3.horizontal.decrease", size: 22)
                        }

                        Button(action: { showNotificationAlert = true }) {
                            ZStack(alignment: .topTrailing) {
                                GradientIcon(systemName: "bell.fill", size: 22)
                                if appState.totalChannelUnread > 0 {
                                    Text("\(appState.totalChannelUnread)")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(.white)
                                        .padding(3)
                                        .background(FestivalTheme.accentPink)
                                        .clipShape(Circle())
                                        .offset(x: 6, y: -6)
                                }
                            }
                        }

                        Button(action: { showCreateChannel = true }) {
                            GradientIcon(systemName: "plus", size: 22)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 8)

                    // Section header
                    HStack {
                        Text("All Channels")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(FestivalTheme.mainGradient)
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)

                    if filteredChannels.isEmpty {
                        Spacer()
                        VStack(spacing: 12) {
                            Image(systemName: "number")
                                .font(.system(size: 72))
                                .foregroundColor(FestivalTheme.textSecondary)
                            Text("No channels yet")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(.white)
                            Text("Create or join a channel to start chatting with groups")
                                .font(.system(size: 15))
                                .foregroundColor(FestivalTheme.textSecondary)
                                .multilineTextAlignment(.center)

                            Button("Create Channel") { showCreateChannel = true }
                                .font(.system(size: 17, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 56)
                                .background(FestivalTheme.mainGradientDiagonal)
                                .clipShape(RoundedRectangle(cornerRadius: 16))
                                .padding(.horizontal, 40)
                                .padding(.top, 8)
                        }
                        .frame(maxWidth: .infinity)
                        Spacer()
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 0) {
                                ForEach(filteredChannels) { channel in
                                    NavigationLink(destination: ChannelChatView(channelId: channel.id)) {
                                        channelRow(channel)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.bottom, 20)
                        }
                    }
                }
            }
            .sheet(isPresented: $showCreateChannel) {
                CreateChannelSheet()
            }
            .sheet(isPresented: $showFilter) {
                NavigationStack {
                    ZStack {
                        FestivalTheme.backgroundBlack.ignoresSafeArea()
                        VStack(spacing: 16) {
                            Text("Filter Channels")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.white)
                            Text("Show only channels that matter to you.")
                                .font(.system(size: 15))
                                .foregroundColor(FestivalTheme.textSecondary)
                                .multilineTextAlignment(.center)
                            Spacer()
                        }
                        .padding(24)
                    }
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Done") { showFilter = false }
                                .foregroundColor(FestivalTheme.accentPink)
                        }
                    }
                }
                .presentationDetents([.medium])
            }
            .alert("Notifications", isPresented: $showNotificationAlert) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("No new notifications")
            }
            .onAppear { appState.startNostrRelay() }
        }
    }

    @ViewBuilder
    private func channelRow(_ channel: ChannelInfo) -> some View {
        HStack(spacing: 12) {
            // Unread dot
            if channel.unreadCount > 0 {
                Circle()
                    .fill(FestivalTheme.accentPink)
                    .frame(width: 10, height: 10)
            } else {
                Spacer().frame(width: 10)
            }

            // Stacked avatar cluster
            stackedAvatars(names: channel.memberAvatarNames, count: channel.memberCount)

            // Channel info
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 4) {
                    Text(channel.name)
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.white)
                        .lineLimit(1)
                    if channel.isVerified {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.blue)
                    }
                }

                if !channel.lastMessage.isEmpty {
                    Text("\(channel.lastMessageSenderName): \(channel.lastMessage)")
                        .font(.system(size: 15))
                        .foregroundColor(FestivalTheme.textSecondary)
                        .lineLimit(1)
                } else {
                    Text("\(channel.memberCount) \(channel.memberCount == 1 ? "member" : "members")")
                        .font(.system(size: 15))
                        .foregroundColor(FestivalTheme.textSecondary)
                }
            }

            Spacer()

            // Timestamp
            if let ts = channel.lastMessageTimestamp {
                Text(ts, style: .relative)
                    .font(.system(size: 13))
                    .foregroundColor(FestivalTheme.textSecondary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(FestivalTheme.backgroundBlack)
    }

    @ViewBuilder
    private func stackedAvatars(names: [String], count: Int) -> some View {
        let displayNames = Array(names.prefix(4))
        let avatarSize: CGFloat = 40
        let overlap: CGFloat = 14
        let totalWidth = displayNames.isEmpty ? avatarSize : avatarSize + CGFloat(displayNames.count - 1) * (avatarSize - overlap)

        ZStack(alignment: .bottomTrailing) {
            HStack(spacing: 0) {
                ZStack(alignment: .leading) {
                    ForEach(Array(displayNames.enumerated()), id: \.offset) { index, name in
                        CircularAvatarView(displayName: name, size: avatarSize)
                            .overlay(
                                Circle().stroke(FestivalTheme.backgroundBlack, lineWidth: 2)
                            )
                            .offset(x: CGFloat(index) * (avatarSize - overlap))
                    }
                }
                .frame(width: totalWidth, height: avatarSize)
            }

            if count > 0 {
                Text("\(count)")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(FestivalTheme.textMuted)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(FestivalTheme.surfaceDark)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .offset(x: 4, y: 4)
            }
        }
    }
}
