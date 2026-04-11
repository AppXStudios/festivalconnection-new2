import Foundation
import CryptoKit
import Security

@MainActor
final class IdentityManager: ObservableObject {
    static let shared = IdentityManager()

    @Published var isInitialized: Bool = false
    @Published var publicKeyHex: String = ""
    @Published var fingerprint: String = ""
    @Published var displayName: String = ""
    @Published var handle: String = ""

    private var signingPrivateKey: Curve25519.Signing.PrivateKey?
    private var signingPublicKey: Curve25519.Signing.PublicKey?

    private let keychainAccountKey = "com.appxstudios.festivalconnection.ed25519"

    func initialize() async {
        if let existingKeyData = loadFromKeychain(account: keychainAccountKey) {
            do {
                signingPrivateKey = try Curve25519.Signing.PrivateKey(rawRepresentation: existingKeyData)
            } catch {
                signingPrivateKey = Curve25519.Signing.PrivateKey()
                saveToKeychain(account: keychainAccountKey, data: signingPrivateKey!.rawRepresentation)
            }
        } else {
            signingPrivateKey = Curve25519.Signing.PrivateKey()
            saveToKeychain(account: keychainAccountKey, data: signingPrivateKey!.rawRepresentation)
        }

        signingPublicKey = signingPrivateKey?.publicKey
        let pubKeyData = signingPublicKey!.rawRepresentation
        publicKeyHex = pubKeyData.map { String(format: "%02x", $0) }.joined()

        let hash = SHA256.hash(data: pubKeyData)
        fingerprint = hash.compactMap { String(format: "%02x", $0) }.joined()

        let defaults = UserDefaults.standard
        if defaults.string(forKey: "fc_nickname")?.isEmpty ?? true {
            let defaultName = "Peer \(String(fingerprint.prefix(4)).uppercased())"
            defaults.set(defaultName, forKey: "fc_nickname")
        }
        if defaults.string(forKey: "fc_handle")?.isEmpty ?? true {
            let defaultHandle = String(fingerprint.prefix(8)).lowercased()
            defaults.set(defaultHandle, forKey: "fc_handle")
        }

        displayName = defaults.string(forKey: "fc_nickname") ?? ""
        handle = defaults.string(forKey: "fc_handle") ?? ""

        await MainActor.run {
            isInitialized = true
        }
    }

    func sign(_ data: Data) -> Data? {
        guard let key = signingPrivateKey else { return nil }
        return try? key.signature(for: data)
    }

    func peerID() -> Data {
        guard let pubKey = signingPublicKey else { return Data(repeating: 0, count: 8) }
        let hash = SHA256.hash(data: pubKey.rawRepresentation)
        return Data(hash.prefix(8))
    }

    // MARK: - Keychain

    private func saveToKeychain(account: String, data: Data) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecAttrService as String: "com.appxstudios.festivalconnection",
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]
        SecItemDelete(query as CFDictionary)

        var addQuery = query
        addQuery[kSecValueData as String] = data
        SecItemAdd(addQuery as CFDictionary, nil)
    }

    private func loadFromKeychain(account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecAttrService as String: "com.appxstudios.festivalconnection",
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess else { return nil }
        return result as? Data
    }
}
