import SwiftUI

struct ChatView: View {
    @EnvironmentObject var appState: AppState
    let peerKey: String
    @State private var messageText = ""
    @State private var showPaymentSheet = false

    private var messages: [ChatMessage] {
        appState.conversations[peerKey] ?? []
    }

    var body: some View {
        ZStack {
            FestivalTheme.backgroundBlack.ignoresSafeArea()

            VStack(spacing: 0) {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(messages) { msg in
                                messageBubble(msg)
                                    .id(msg.id)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                    }
                    .onChange(of: messages.count) { _ in
                        if let last = messages.last {
                            proxy.scrollTo(last.id, anchor: .bottom)
                        }
                    }
                }

                Divider().background(FestivalTheme.surfaceDark)

                // Input bar
                HStack(spacing: 8) {
                    Button(action: { showPaymentSheet = true }) {
                        Text("\u{20BF}")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(FestivalTheme.paymentBorder)
                    }

                    TextField("Message", text: $messageText)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(FestivalTheme.surfaceMedium)
                        .clipShape(RoundedRectangle(cornerRadius: 20))
                        .foregroundColor(.white)
                        .submitLabel(.send)

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
                HStack(spacing: 8) {
                    CircularAvatarView(
                        displayName: appState.peerName(for: peerKey),
                        profileImageData: appState.peerImageData(for: peerKey),
                        size: 36
                    )
                    Text(appState.peerName(for: peerKey))
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundColor(.white)
                }
            }
        }
        .sheet(isPresented: $showPaymentSheet) {
            PaymentRequestSheet(peerKey: peerKey)
        }
        .onAppear {
            appState.clearUnread(forPeer: peerKey)
        }
    }

    private func sendMessage() {
        let trimmed = messageText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        let msg = ChatMessage(
            senderKey: IdentityManager.shared.publicKeyHex,
            recipientKey: peerKey,
            content: trimmed,
            isIncoming: false
        )
        appState.addMessage(msg, forPeer: peerKey)

        // Send via NIP-04 encrypted DM over Nostr
        if let dmEvent = NostrDM.createDirectMessage(to: peerKey, content: trimmed) {
            NostrRelayManager.shared.publishEvent(dmEvent)
        }

        // Send via mesh (BLE + Multipeer) through PacketProcessor
        let recipientHexPrefix = String(peerKey.prefix(16))
        var recipientID = Data()
        var rIdx = recipientHexPrefix.startIndex
        while rIdx < recipientHexPrefix.endIndex {
            let next = recipientHexPrefix.index(rIdx, offsetBy: 2, limitedBy: recipientHexPrefix.endIndex) ?? recipientHexPrefix.endIndex
            if let byte = UInt8(recipientHexPrefix[rIdx..<next], radix: 16) {
                recipientID.append(byte)
            }
            rIdx = next
        }
        PacketProcessor.shared.sendMessage(
            content: trimmed,
            senderID: IdentityManager.shared.peerID(),
            recipientID: recipientID
        )

        messageText = ""
    }

    @ViewBuilder
    private func messageBubble(_ msg: ChatMessage) -> some View {
        if msg.messageType == 0xF0 {
            // Payment request bubble
            paymentRequestBubble(msg)
        } else if msg.messageType == 0xF1 {
            // Payment notification
            paymentNotificationBubble(msg)
        } else if msg.isIncoming {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
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
                .clipShape(RoundedCornerShape(corners: [.topLeft, .topRight, .bottomRight], radius: 18))
                Spacer(minLength: 60)
            }
        } else {
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
                .clipShape(RoundedCornerShape(corners: [.topLeft, .topRight, .bottomLeft], radius: 18))
            }
        }
    }

    @ViewBuilder
    private func paymentRequestBubble(_ msg: ChatMessage) -> some View {
        VStack(spacing: 8) {
            Text("\u{20BF}")
                .font(.system(size: 14))
                .foregroundColor(FestivalTheme.textSecondary)
            if let amount = msg.paymentAmount {
                Text("\(amount) sats")
                    .font(.system(size: 24, weight: .heavy))
                    .foregroundColor(.white)
            }
            if let desc = msg.paymentDescription {
                Text(desc)
                    .font(.system(size: 14))
                    .foregroundColor(FestivalTheme.textSecondary)
            }
            if !msg.paymentConfirmed {
                Button("Pay Now") {
                    if let invoice = msg.paymentInvoice {
                        Task {
                            do {
                                try await WalletManager.shared.sendPayment(invoice: invoice, amountSat: msg.paymentAmount ?? 0)
                                await MainActor.run {
                                    appState.setPaymentConfirmed(messageId: msg.id, forPeer: peerKey)
                                }
                            } catch {
                                // Payment failed — button stays visible for retry
                            }
                        }
                    }
                }
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(FestivalTheme.mainGradientDiagonal)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            } else {
                HStack {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(FestivalTheme.presenceGreen)
                    Text("Paid")
                        .foregroundColor(FestivalTheme.presenceGreen)
                        .font(.system(size: 15, weight: .semibold))
                }
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .stroke(FestivalTheme.paymentBorder, lineWidth: 2)
                .background(FestivalTheme.surfaceDark.clipShape(RoundedRectangle(cornerRadius: 16)))
        )
        .frame(maxWidth: 280)
        .frame(maxWidth: .infinity, alignment: msg.isIncoming ? .leading : .trailing)
    }

    @ViewBuilder
    private func paymentNotificationBubble(_ msg: ChatMessage) -> some View {
        HStack(spacing: 8) {
            Text("\u{20BF}")
                .foregroundColor(FestivalTheme.textSecondary)
            if let amount = msg.paymentAmount {
                Text("\(amount) sats")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.white)
            }
            Image(systemName: msg.isIncoming ? "arrow.down" : "arrow.up")
                .foregroundColor(FestivalTheme.presenceGreen)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(FestivalTheme.mainGradient.opacity(0.3))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .frame(maxWidth: .infinity, alignment: msg.isIncoming ? .leading : .trailing)
    }
}

struct PaymentRequestSheet: View {
    @EnvironmentObject var appState: AppState
    @Environment(\.dismiss) var dismiss
    let peerKey: String
    @State private var amount = ""
    @State private var description = ""
    @State private var isLoading = false

    var displayAmount: String {
        guard let val = Double(amount) else { return "$0.00" }
        return String(format: "$%.2f", val / 100.0)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                VStack(spacing: 20) {
                    Text("Request Payment")
                        .font(.system(size: 24, weight: .heavy))
                        .foregroundStyle(FestivalTheme.mainGradient)
                        .padding(.top, 20)

                    Text(displayAmount)
                        .font(.system(size: 36, weight: .heavy))
                        .foregroundColor(.white)

                    TextField("Description", text: $description)
                        .foregroundColor(.white)
                        .autocorrectionDisabled()
                        .padding(14)
                        .background(FestivalTheme.surfaceMedium)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .padding(.horizontal, 24)

                    NumericKeypad(amount: $amount)

                    Button(action: {
                        isLoading = true
                        let amountVal = UInt64(amount) ?? 0
                        var msg = ChatMessage(
                            senderKey: IdentityManager.shared.publicKeyHex,
                            recipientKey: peerKey,
                            content: "Payment Request",
                            isIncoming: false,
                            messageType: 0xF0
                        )
                        msg.paymentAmount = amountVal
                        msg.paymentDescription = description.isEmpty ? nil : description
                        appState.addMessage(msg, forPeer: peerKey)
                        isLoading = false
                        dismiss()
                    }) {
                        if isLoading {
                            ProgressView().tint(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 56)
                                .background(FestivalTheme.mainGradientDiagonal)
                                .clipShape(RoundedRectangle(cornerRadius: 16))
                        } else {
                            Text("Generate Request")
                                .font(.system(size: 17, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 56)
                                .background(amount.isEmpty ? AnyShapeStyle(FestivalTheme.textMuted) : AnyShapeStyle(FestivalTheme.mainGradientDiagonal))
                                .clipShape(RoundedRectangle(cornerRadius: 16))
                        }
                    }
                    .disabled(amount.isEmpty || isLoading)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 20)
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

struct RoundedCornerShape: Shape {
    var corners: UIRectCorner
    var radius: CGFloat

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}
