import Foundation
import BreezSDKLiquid

@MainActor
final class WalletManager: ObservableObject {
    static let shared = WalletManager()

    @Published var balanceSat: UInt64 = 0
    @Published var balanceUSD: Double = 0.0
    @Published var transactions: [WalletTransaction] = []
    @Published var isConnected: Bool = false
    @Published var connectionError: String?
    /// Live sats-per-USD rate, refreshed alongside the balance from Breez fetchFiatRates().
    /// Falls back to ~1100 sats/$ until the first successful fetch.
    @Published var satPerUSD: Double = 1100

    private var sdk: BindingLiquidSdk?
    private var eventListenerId: String?
    private let keychainAccount = "fc_wallet_mnemonic"

    // MARK: - Lifecycle

    func connect() {
        guard sdk == nil else { return }
        do {
            let mnemonic = getOrCreateMnemonic()
            var config = try defaultConfig(network: LiquidNetwork.mainnet, breezApiKey: "MIIBeTCCASugAwIBAgIHPxUDqQ19mTAFBgMrZXAwEDEOMAwGA1UEAxMFQnJlZXowHhcNMjYwNDA3MjMwMTM5WhcNMzYwNDA0MjMwMTM5WjAvMRQwEgYDVQQKEwtBUFBYU3R1ZGlvczEXMBUGA1UEAxMOQWJyYWhhbSBWYXJnYXMwKjAFBgMrZXADIQDQg")
            config.workingDir = walletDirectory()
            let req = ConnectRequest(config: config, mnemonic: mnemonic)
            sdk = try BreezSDKLiquid.connect(req: req)
            isConnected = true
            connectionError = nil
            // Set up event listener — addEventListener returns an ID string
            eventListenerId = try sdk?.addEventListener(listener: self)
            // Initial data fetch
            refreshBalance()
            refreshTransactions()
        } catch {
            connectionError = error.localizedDescription
            isConnected = false
        }
    }

    func disconnect() {
        if let listenerId = eventListenerId {
            try? sdk?.removeEventListener(id: listenerId)
            eventListenerId = nil
        }
        try? sdk?.disconnect()
        sdk = nil
        isConnected = false
    }

    // MARK: - Balance

    func refreshBalance() {
        guard let sdk = sdk else { return }
        Task {
            do {
                let info = try sdk.getInfo()
                // walletInfo.balanceSat is UInt64 (non-optional)
                let btcBalance = info.walletInfo.balanceSat
                balanceSat = btcBalance
                // Fetch fiat rate for USD conversion
                if let rates = try? sdk.fetchFiatRates() {
                    if let usdRate = rates.first(where: { $0.coin == "USD" }) {
                        balanceUSD = Double(btcBalance) / 100_000_000.0 * usdRate.value
                        // usdRate.value = USD per BTC. 1 BTC = 100_000_000 sats.
                        // sats per $1 = 100_000_000 / USD-per-BTC
                        if usdRate.value > 0 {
                            satPerUSD = 100_000_000.0 / usdRate.value
                        }
                    }
                }
            } catch {
                print("[Wallet] Balance refresh failed: \(error)")
            }
        }
    }

    // MARK: - Receive (Create Invoice)

    func createInvoice(amountSat: UInt64, description: String = "") async throws -> String {
        guard let sdk = sdk else { throw WalletError.notConnected }
        // PrepareReceiveRequest takes paymentMethod + optional ReceiveAmount
        // ReceiveAmount.bitcoin uses payerAmountSat:
        let prepareReq = PrepareReceiveRequest(
            paymentMethod: .bolt11Invoice,
            amount: .bitcoin(payerAmountSat: amountSat)
        )
        let prepareResponse = try sdk.prepareReceivePayment(req: prepareReq)
        // ReceivePaymentRequest takes prepareResponse (not individual fields)
        let receiveReq = ReceivePaymentRequest(
            prepareResponse: prepareResponse,
            description: description
        )
        let response = try sdk.receivePayment(req: receiveReq)
        return response.destination
    }

    // MARK: - Send (Pay Invoice)

    func sendPayment(invoice: String, amountSat: UInt64? = nil) async throws {
        guard let sdk = sdk else { throw WalletError.notConnected }
        // PrepareSendRequest takes destination + optional PayAmount
        // PayAmount.bitcoin uses receiverAmountSat:
        let prepareReq: PrepareSendRequest
        if let amt = amountSat {
            prepareReq = PrepareSendRequest(destination: invoice, amount: .bitcoin(receiverAmountSat: amt))
        } else {
            prepareReq = PrepareSendRequest(destination: invoice)
        }
        let prepareResponse = try sdk.prepareSendPayment(req: prepareReq)
        // SendPaymentRequest takes prepareResponse (not destination)
        let sendReq = SendPaymentRequest(prepareResponse: prepareResponse)
        let _ = try sdk.sendPayment(req: sendReq)
        // Refresh after payment
        refreshBalance()
        refreshTransactions()
    }

    // MARK: - Parse Invoice

    func parseInvoice(_ input: String) throws -> (amountSat: UInt64?, description: String?) {
        guard let sdk = sdk else { throw WalletError.notConnected }
        let parsed = try sdk.parse(input: input)
        switch parsed {
        case .bolt11(let invoice):
            // LnInvoice has amountMsat: UInt64? and description: String?
            return (amountSat: invoice.amountMsat.map { $0 / 1000 }, description: invoice.description)
        case .bolt12Offer(let offer, _):
            // bolt12Offer has two associated values: (offer: LnOffer, bip353Address: String?)
            // LnOffer.minAmount is Amount? with case .bitcoin(amountMsat:)
            var offerAmountSat: UInt64? = nil
            if let minAmount = offer.minAmount {
                switch minAmount {
                case .bitcoin(let amountMsat):
                    offerAmountSat = amountMsat / 1000
                case .currency:
                    break
                }
            }
            return (amountSat: offerAmountSat, description: offer.description)
        default:
            return (amountSat: nil, description: nil)
        }
    }

    // MARK: - Transaction History

    func refreshTransactions() {
        guard let sdk = sdk else { return }
        Task {
            do {
                let payments = try sdk.listPayments(req: ListPaymentsRequest())
                transactions = payments.compactMap { payment -> WalletTransaction? in
                    let direction: String
                    switch payment.paymentType {
                    case .receive: direction = "received"
                    case .send: direction = "sent"
                    }
                    // Extract description from PaymentDetails (Payment has no .description)
                    let desc: String
                    switch payment.details {
                    case .lightning(_, let description, _, _, _, _, _, _, _, _, _, _, _, _, _):
                        desc = description
                    case .liquid(_, _, let description, _, _, _, _):
                        desc = description
                    case .bitcoin(_, _, let description, _, _, _, _, _, _, _):
                        desc = description
                    }
                    return WalletTransaction(
                        id: payment.txId ?? UUID().uuidString,
                        amountSat: payment.amountSat,
                        amountUSD: 0,  // Will be calculated with fiat rate
                        direction: direction,
                        timestamp: Date(timeIntervalSince1970: TimeInterval(payment.timestamp)),
                        description: desc,
                        paymentHash: payment.txId ?? "",
                        status: String(describing: payment.status),
                        fees: payment.feesSat
                    )
                }
                // Apply fiat rates to transactions
                if let rates = try? sdk.fetchFiatRates() {
                    if let usdRate = rates.first(where: { $0.coin == "USD" }) {
                        for i in self.transactions.indices {
                            self.transactions[i].amountUSD = Double(self.transactions[i].amountSat) / 100_000_000.0 * usdRate.value
                        }
                    }
                }
            } catch {
                print("[Wallet] Transaction refresh failed: \(error)")
            }
        }
    }

    // MARK: - Mnemonic Management

    /// Returns a BIP-39 mnemonic string, generating + persisting one on first run.
    /// Old raw-byte "seeds" stored under this same Keychain account are intentionally
    /// not migrated — they were never valid BIP-39 phrases, so any funds attached
    /// would have been unrecoverable cross-device anyway.
    private func getOrCreateMnemonic() -> String {
        if let existing = loadMnemonicFromKeychain(), BIP39.isValidMnemonic(existing) {
            return existing
        }
        let mnemonic = BIP39.generate12WordMnemonic()
        saveMnemonicToKeychain(mnemonic)
        return mnemonic
    }

    private func saveMnemonicToKeychain(_ mnemonic: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainAccount,
            kSecAttrService as String: "com.appxstudios.festivalconnection.wallet",
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]
        SecItemDelete(query as CFDictionary)
        var addQuery = query
        addQuery[kSecValueData as String] = Data(mnemonic.utf8)
        SecItemAdd(addQuery as CFDictionary, nil)
    }

    private func loadMnemonicFromKeychain() -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainAccount,
            kSecAttrService as String: "com.appxstudios.festivalconnection.wallet",
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data,
              let str = String(data: data, encoding: .utf8) else { return nil }
        return str
    }

    private func walletDirectory() -> String {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            .appendingPathComponent("breez-wallet", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.path
    }

    enum WalletError: Error, LocalizedError {
        case notConnected
        var errorDescription: String? {
            switch self {
            case .notConnected: return "Wallet not connected"
            }
        }
    }
}

// MARK: - Event Listener
extension WalletManager: EventListener {
    nonisolated func onEvent(e: SdkEvent) {
        Task { @MainActor in
            switch e {
            case .paymentSucceeded(_), .paymentPending(_):
                refreshBalance()
                refreshTransactions()
            case .synced:
                refreshBalance()
            default:
                break
            }
        }
    }
}
