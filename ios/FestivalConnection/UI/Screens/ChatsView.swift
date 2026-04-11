import SwiftUI

struct ChatsView: View {
    @EnvironmentObject var appState: AppState
    @State private var searchText = ""
    @State private var showNewChat = false

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                VStack(spacing: 0) {
                    // Header
                    HStack {
                        Text("Festival Connection")
                            .font(.system(size: 34, weight: .heavy))
                            .foregroundStyle(FestivalTheme.mainGradient)
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 8)

                    // Search bar
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(FestivalTheme.textMuted)
                            .font(.system(size: 16))
                        TextField("Search", text: $searchText)
                            .foregroundColor(FestivalTheme.textPrimary)
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                    }
                    .padding(.vertical, 10)
                    .padding(.horizontal, 12)
                    .background(FestivalTheme.surfaceMedium)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal, 16)
                    .padding(.bottom, 8)

                    if appState.sortedConversations.isEmpty {
                        Spacer()
                        VStack(spacing: 12) {
                            Image(systemName: "bubble.left.and.bubble.right.fill")
                                .font(.system(size: 72))
                                .foregroundColor(FestivalTheme.textSecondary)
                            Text("No conversations yet")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(FestivalTheme.textPrimary)
                            Text("People nearby will appear when connected")
                                .font(.system(size: 15))
                                .foregroundColor(FestivalTheme.textSecondary)
                        }
                        Spacer()
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 0) {
                                ForEach(appState.sortedConversations, id: \.peerKey) { conv in
                                    NavigationLink(destination: ChatView(peerKey: conv.peerKey)) {
                                        conversationRow(peerKey: conv.peerKey, messages: conv.messages)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .overlay(alignment: .bottomTrailing) {
                Button(action: { showNewChat = true }) {
                    Image(systemName: "square.and.pencil")
                        .font(.system(size: 22))
                        .foregroundColor(.white)
                        .frame(width: 56, height: 56)
                        .background(FestivalTheme.mainGradientDiagonal)
                        .clipShape(Circle())
                        .shadow(color: .black.opacity(0.3), radius: 8)
                }
                .padding(.trailing, 20)
                .padding(.bottom, 20)
            }
            .sheet(isPresented: $showNewChat) {
                NewChatView()
            }
        }
    }

    private func conversationRow(peerKey: String, messages: [ChatMessage]) -> some View {
        HStack(spacing: 12) {
            CircularAvatarView(
                displayName: appState.peerName(for: peerKey),
                profileImageData: appState.peerImageData(for: peerKey),
                size: 52
            )

            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(appState.peerName(for: peerKey))
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(FestivalTheme.textPrimary)
                    Spacer()
                    if let last = messages.last {
                        Text(last.timestamp, style: .time)
                            .font(.system(size: 13))
                            .foregroundColor(FestivalTheme.textSecondary)
                    }
                }
                HStack {
                    Text(messages.last?.content ?? "")
                        .font(.system(size: 15))
                        .foregroundColor(FestivalTheme.textSecondary)
                        .lineLimit(1)
                    Spacer()
                    if let count = appState.unreadCounts[peerKey], count > 0 {
                        Text("\(count)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 20, height: 20)
                            .background(FestivalTheme.accentPink)
                            .clipShape(Circle())
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(FestivalTheme.backgroundBlack)
    }
}
