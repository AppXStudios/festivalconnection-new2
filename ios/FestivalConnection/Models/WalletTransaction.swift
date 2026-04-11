import Foundation

struct WalletTransaction: Identifiable, Equatable {
    let id: String
    let amountSat: UInt64
    var amountUSD: Double
    let direction: String // "sent" or "received"
    let timestamp: Date
    var description: String
    var paymentHash: String
    var status: String
    var fees: UInt64 = 0
}

struct WalletAlert: Identifiable {
    let id = UUID()
    let amountSat: UInt64
    let amountUSD: Double
    let description: String
    let timestamp: Date
}
