import SwiftUI

struct TransactionHistoryView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject private var wallet = WalletManager.shared

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                VStack(spacing: 0) {
                    Text("Transaction History")
                        .font(.system(size: 28, weight: .heavy))
                        .foregroundColor(.white)
                        .padding(.top, 20)
                        .padding(.bottom, 16)

                    if wallet.transactions.isEmpty {
                        Spacer()
                        VStack(spacing: 8) {
                            Image(systemName: "clock.arrow.circlepath")
                                .font(.system(size: 48))
                                .foregroundColor(FestivalTheme.textSecondary)
                            Text("No transactions yet")
                                .font(.system(size: 15))
                                .foregroundColor(FestivalTheme.textSecondary)
                        }
                        Spacer()
                    } else {
                        ScrollView {
                            LazyVStack(spacing: 8) {
                                ForEach(wallet.transactions) { tx in
                                    HStack(spacing: 12) {
                                        Image(systemName: tx.direction == "received" ? "arrow.down.circle.fill" : "arrow.up.circle.fill")
                                            .font(.system(size: 32))
                                            .foregroundColor(tx.direction == "received" ? FestivalTheme.presenceGreen : FestivalTheme.accentPink)
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(tx.description)
                                                .font(.system(size: 15, weight: .medium))
                                                .foregroundColor(.white)
                                            Text(tx.timestamp, style: .date)
                                                .font(.system(size: 13))
                                                .foregroundColor(FestivalTheme.textSecondary)
                                        }
                                        Spacer()
                                        VStack(alignment: .trailing) {
                                            Text(String(format: "%@$%.2f", tx.direction == "received" ? "+" : "-", tx.amountUSD))
                                                .font(.system(size: 15, weight: .semibold))
                                                .foregroundColor(tx.direction == "received" ? FestivalTheme.presenceGreen : FestivalTheme.accentPink)
                                            Text("\(tx.amountSat) sats")
                                                .font(.system(size: 12))
                                                .foregroundColor(FestivalTheme.textMuted)
                                        }
                                    }
                                    .padding(14)
                                    .background(FestivalTheme.surfaceDark)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                        .foregroundColor(FestivalTheme.accentPink)
                }
            }
            .onAppear { wallet.refreshTransactions() }
        }
    }
}
