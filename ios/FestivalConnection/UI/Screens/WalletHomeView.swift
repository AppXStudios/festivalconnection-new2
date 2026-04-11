import SwiftUI

struct WalletHomeView: View {
    @ObservedObject private var wallet = WalletManager.shared
    @State private var showPay = false
    @State private var showRequest = false
    @State private var showAddFunds = false
    @State private var showHistory = false

    var body: some View {
        NavigationStack {
            ZStack {
                FestivalTheme.backgroundBlack.ignoresSafeArea()

                VStack(spacing: 0) {
                    HStack {
                        Text("Wallet")
                            .font(.system(size: 34, weight: .heavy))
                            .foregroundColor(.white)
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 16)

                    // Balance card
                    VStack(spacing: 8) {
                        Text("YOUR BALANCE")
                            .font(.system(size: 14, weight: .medium))
                            .tracking(1)
                            .foregroundStyle(FestivalTheme.mainGradient)

                        Text(String(format: "$%.2f", wallet.balanceUSD))
                            .font(.system(size: 48, weight: .heavy))
                            .foregroundStyle(FestivalTheme.mainGradient)

                        Text(String(format: "%.8f BTC", Double(wallet.balanceSat) / 100_000_000.0))
                            .font(.system(size: 15))
                            .foregroundColor(FestivalTheme.textSecondary)
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity)
                    .background(FestivalTheme.surfaceDark)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    .padding(.horizontal, 16)

                    // Quick actions
                    HStack(spacing: 12) {
                        quickAction(icon: "creditcard.fill", label: "Add Funds") { showAddFunds = true }
                        quickAction(icon: "clock.arrow.circlepath", label: "History") { showHistory = true }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)

                    // Recent Activity
                    HStack {
                        Text("RECENT ACTIVITY")
                            .font(.system(size: 13, weight: .semibold))
                            .tracking(1)
                            .foregroundStyle(FestivalTheme.mainGradient)
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 20)
                    .padding(.bottom, 8)

                    if wallet.transactions.isEmpty {
                        Spacer()
                        VStack(spacing: 8) {
                            Image(systemName: "bolt.fill")
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
                                    transactionRow(tx)
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }

                    // Bottom buttons
                    HStack(spacing: 12) {
                        Button(action: { showPay = true }) {
                            Text("Pay")
                                .font(.system(size: 17, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 56)
                                .background(FestivalTheme.mainGradientDiagonal)
                                .clipShape(RoundedRectangle(cornerRadius: 16))
                        }

                        Button(action: { showRequest = true }) {
                            Text("Request")
                                .font(.system(size: 17, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 56)
                                .background(FestivalTheme.mainGradientDiagonal)
                                .clipShape(RoundedRectangle(cornerRadius: 16))
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }
            }
            .sheet(isPresented: $showPay) { PayView() }
            .sheet(isPresented: $showRequest) { RequestView() }
            .sheet(isPresented: $showAddFunds) { AddFundsView() }
            .sheet(isPresented: $showHistory) { TransactionHistoryView() }
            .onAppear { wallet.refreshBalance(); wallet.refreshTransactions() }
        }
    }

    private func quickAction(icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 18))
                    .foregroundColor(.white)
                Text(label)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.white)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(FestivalTheme.surfaceDark)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private func transactionRow(_ tx: WalletTransaction) -> some View {
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

            Text(String(format: "%@$%.2f", tx.direction == "received" ? "+" : "-", tx.amountUSD))
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(tx.direction == "received" ? FestivalTheme.presenceGreen : FestivalTheme.accentPink)
        }
        .padding(14)
        .background(FestivalTheme.surfaceDark)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}
