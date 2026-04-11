import SwiftUI
import CoreImage.CIFilterBuiltins

struct InvoiceDisplayView: View {
    @Environment(\.dismiss) var dismiss
    let invoice: String
    let amountUSD: Double
    let description: String
    @State private var secondsRemaining = 600
    let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var timeString: String {
        let m = secondsRemaining / 60
        let s = secondsRemaining % 60
        return String(format: "%d:%02d", m, s)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                VStack(spacing: 20) {
                    Text("Share Payment Request")
                        .font(.system(size: 24, weight: .heavy))
                        .foregroundColor(.white)
                        .padding(.top, 20)

                    if let qrImage = generateQR(from: invoice.isEmpty ? "festivalconnection://payment" : invoice) {
                        Image(uiImage: qrImage)
                            .interpolation(.none)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 220, height: 220)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }

                    Text(String(format: "$%.2f", amountUSD))
                        .font(.system(size: 28, weight: .heavy))
                        .foregroundColor(.white)

                    if !description.isEmpty {
                        Text(description)
                            .font(.system(size: 15))
                            .foregroundColor(FestivalTheme.textSecondary)
                    }

                    Text("Expires in \(timeString)")
                        .font(.system(size: 13))
                        .foregroundColor(FestivalTheme.textMuted)

                    HStack(spacing: 20) {
                        Button("Copy") {
                            UIPasteboard.general.string = invoice
                        }
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(FestivalTheme.accentPink)

                        ShareLink(item: invoice) {
                            Text("Share")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(FestivalTheme.accentPink)
                        }
                    }

                    Spacer()
                }
            }
            .onReceive(timer) { _ in
                if secondsRemaining > 0 { secondsRemaining -= 1 }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                        .foregroundColor(FestivalTheme.accentPink)
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
