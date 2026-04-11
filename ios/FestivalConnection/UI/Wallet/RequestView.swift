import SwiftUI

struct RequestView: View {
    @Environment(\.dismiss) var dismiss
    @State private var amount = ""
    @State private var description = ""
    @State private var showInvoice = false
    @State private var generatedInvoice = ""

    var displayAmount: String {
        guard let val = Double(amount) else { return "$0.00" }
        return String(format: "$%.2f", val / 100.0)
    }

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                VStack(spacing: 20) {
                    Text("Request")
                        .font(.system(size: 28, weight: .heavy))
                        .foregroundColor(.white)
                        .padding(.top, 20)

                    Text(displayAmount)
                        .font(.system(size: 48, weight: .heavy))
                        .foregroundColor(.white)

                    TextField("Description", text: $description)
                        .foregroundColor(.white)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .padding(14)
                        .background(FestivalTheme.surfaceMedium)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .padding(.horizontal, 24)

                    NumericKeypad(amount: $amount)

                    Button(action: {
                        Task {
                            do {
                                let amountCents = Double(amount) ?? 0
                                let amountSat = UInt64(amountCents / 100.0 * 100_000_000.0 / 50_000.0) // rough USD->sat
                                generatedInvoice = try await WalletManager.shared.createInvoice(amountSat: amountSat, description: description)
                                showInvoice = true
                            } catch {
                                generatedInvoice = "Error: \(error.localizedDescription)"
                            }
                        }
                    }) {
                        Text("Create Payment Request")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(FestivalTheme.mainGradientDiagonal)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                    }
                    .disabled(amount.isEmpty)
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
            .sheet(isPresented: $showInvoice) {
                InvoiceDisplayView(invoice: generatedInvoice, amountUSD: (Double(amount) ?? 0) / 100.0, description: description)
            }
        }
    }
}
