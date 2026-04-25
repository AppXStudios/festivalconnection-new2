import SwiftUI

struct ChannelChatView: View {
    @EnvironmentObject var appState: AppState
    let channelId: String
    @State private var messageText = ""
    @State private var showOptions = false

    private var channel: ChannelInfo? {
        appState.channels[channelId]
    }

    private var messages: [ChannelMessage] {
        appState.channelMessages[channelId] ?? []
    }

    var body: some View {
        ZStack {
            FestivalTheme.backgroundBlack.ignoresSafeArea()

            VStack(spacing: 0) {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(messages) { msg in
                                channelBubble(msg)
                                    .id(msg.id)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                    }
                    .onChange(of: messages.count) { _ in
                        if let last = messages.last {
                            withAnimation {
                                proxy.scrollTo(last.id, anchor: .bottom)
                            }
                        }
                    }
                }

                Divider().background(FestivalTheme.surfaceDark)

                // Input bar
                HStack(spacing: 8) {
                    TextField("Message", text: $messageText)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(FestivalTheme.surfaceMedium)
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                        .foregroundColor(.white)
                        .submitLabel(.send)
                        .onSubmit { sendMessage() }

                    Button(action: sendMessage) {
                        if messageText.isEmpty {
                            Image(systemName: "arrow.up.circle.fill")
                                .font(.system(size: 32))
                                .foregroundColor(FestivalTheme.textMuted)
                        } else {
                            GradientIcon(systemName: "arrow.up.circle.fill", size: 32)
                        }
                    }
                    .disabled(messageText.isEmpty)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(FestivalTheme.backgroundBlack)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                VStack(spacing: 1) {
                    Text(channel?.name ?? "Channel")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                    HStack(spacing: 4) {
                        Circle()
                            .fill(appState.relayConnected ? FestivalTheme.presenceGreen : Color.red)
                            .frame(width: 6, height: 6)
                        Text("\(channel?.memberCount ?? 0) \((channel?.memberCount ?? 0) == 1 ? "member" : "members")")
                            .font(.system(size: 13))
                            .foregroundColor(FestivalTheme.textSecondary)
                    }
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    if channel?.isJoined == true {
                        Button(role: .destructive) {
                            appState.leaveChannel(id: channelId)
                        } label: {
                            Label("Leave Channel", systemImage: "arrow.right.square")
                        }
                    } else {
                        Button {
                            appState.joinChannel(id: channelId)
                        } label: {
                            Label("Join Channel", systemImage: "plus.circle")
                        }
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .foregroundColor(.white)
                }
            }
        }
        .onAppear {
            appState.clearChannelUnread(channelId: channelId)
            if !appState.channelMemberships.contains(channelId) {
                appState.joinChannel(id: channelId)
            }
            appState.subscribeToChannelMessages(channelId)
        }
        .onDisappear {
            appState.unsubscribeFromChannelMessages(channelId)
        }
    }

    private func sendMessage() {
        guard !messageText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        let myKey = IdentityManager.shared.publicKeyHex
        let myName = UserDefaults.standard.string(forKey: "fc_nickname") ?? IdentityManager.shared.displayName

        // Build the Nostr event first so we can re-use its id locally; addChannelMessage
        // dedups by id, so when the relay echo arrives it will be ignored.
        let nostrEvent = NostrChannels.sendChannelMessage(channelId: channelId, content: messageText)

        let msg = ChannelMessage(
            id: nostrEvent.id,
            channelId: channelId,
            senderPublicKeyHex: myKey,
            senderDisplayName: myName,
            content: messageText
        )
        appState.addChannelMessage(msg)

        // Publish to Nostr relays (kind-42, NIP-28)
        let relayCount = NostrRelayManager.shared.publishEvent(nostrEvent)
        if relayCount == 0 {
            print("[ChannelChat] Message not broadcast to relays — none connected")
        }

        messageText = ""
    }

    @ViewBuilder
    private func channelBubble(_ msg: ChannelMessage) -> some View {
        if msg.isFromLocalUser {
            HStack {
                Spacer(minLength: 60)
                VStack(alignment: .trailing, spacing: 2) {
                    Text(msg.content)
                        .font(.system(size: 16))
                        .foregroundColor(.white)
                    Text(msg.timestamp, style: .time)
                        .font(.system(size: 11))
                        .foregroundColor(.white.opacity(0.7))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(FestivalTheme.mainGradientDiagonal)
                .clipShape(RoundedRectangle(cornerRadius: 18))
            }
        } else {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(msg.senderDisplayName)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(FestivalTheme.mainGradient)
                    Text(msg.content)
                        .font(.system(size: 16))
                        .foregroundColor(.white)
                    Text(msg.timestamp, style: .time)
                        .font(.system(size: 11))
                        .foregroundColor(FestivalTheme.textMuted)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(FestivalTheme.surfaceDark)
                .clipShape(RoundedRectangle(cornerRadius: 18))
                Spacer(minLength: 60)
            }
        }
    }
}
