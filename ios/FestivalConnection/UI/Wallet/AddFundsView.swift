import SwiftUI
import CoreImage.CIFilterBuiltins

struct AddFundsView: View {
    @Environment(\.dismiss) var dismiss
    @State private var showReceiveAddress = false

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                VStack(spacing: 16) {
                    Text("Add Funds")
                        .font(.system(size: 28, weight: .heavy))
                        .foregroundColor(.white)
                        .padding(.top, 20)

                    Button {
                        if let url = URL(string: "https://app.ramp.network/?hostAppName=FestivalConnection&swapAsset=BTC_BTC") {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        fundCard(
                            icon: "creditcard.fill",
                            title: "Buy with Card",
                            subtitle: "Buy instantly with Ramp"
                        )
                    }
                    .buttonStyle(.plain)

                    Button {
                        showReceiveAddress = true
                    } label: {
                        fundCard(
                            icon: "arrow.down.circle.fill",
                            title: "Receive Lightning Payment",
                            subtitle: "Open invoice — sender chooses amount"
                        )
                    }
                    .buttonStyle(.plain)

                    Spacer()
                }
                .padding(.horizontal, 16)
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .foregroundColor(FestivalTheme.accentPink)
                }
            }
            .sheet(isPresented: $showReceiveAddress) {
                ReceiveAddressSheet()
            }
        }
    }

    private func fundCard(icon: String, title: String, subtitle: String) -> some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 28))
                .foregroundColor(.white)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(.white)
                Text(subtitle)
                    .font(.system(size: 14))
                    .foregroundColor(FestivalTheme.textSecondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .foregroundColor(FestivalTheme.textMuted)
        }
        .padding(16)
        .background(FestivalTheme.surfaceDark)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

struct ReceiveAddressSheet: View {
    @Environment(\.dismiss) var dismiss
    @State private var copied = false
    @State private var walletAddress = "Loading..."
    @State private var isLoading = true

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                VStack(spacing: 20) {
                    Text("Lightning Invoice")
                        .font(.system(size: 24, weight: .heavy))
                        .foregroundColor(.white)
                        .padding(.top, 20)

                    if let qrImage = generateQR(from: walletAddress) {
                        Image(uiImage: qrImage)
                            .interpolation(.none)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 220, height: 220)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    Text("Lightning open invoice — sender chooses any amount")
                        .font(.system(size: 13))
                        .foregroundColor(FestivalTheme.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)

                    Button(action: {
                        UIPasteboard.general.string = walletAddress
                        copied = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2) { copied = false }
                    }) {
                        Text(copied ? "Copied!" : "Copy Invoice")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 12)
                            .background(FestivalTheme.surfaceDark)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    Spacer()
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                        .foregroundColor(FestivalTheme.accentPink)
                }
            }
            .onAppear {
                Task {
                    do {
                        walletAddress = try await WalletManager.shared.createInvoice(amountSat: 0, description: "Festival Connection Wallet")
                    } catch {
                        walletAddress = "Unable to generate address"
                    }
                    isLoading = false
                }
            }
        }
    }

    private func generateQR(from string: String) -> UIImage? {
        let context = CIContext()
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(string.utf8)
        filter.correctionLevel = "M"
        guard let outputImage = filter.outputImage else { return nil }
        let scaled = outputImage.transformed(by: CGAffineTransform(scaleX: 10, y: 10))
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
