import Foundation
import CryptoKit
import Security

@MainActor
final class NostrIdentity: ObservableObject {
    static let shared = NostrIdentity()

    @Published var isInitialized = false
    @Published var publicKeyHex = ""

    private var privateKeyBytes: Data?
    private let keychainAccount = "fc_nostr_privkey"

    func initialize() {
        if let existing = loadFromKeychain() {
            privateKeyBytes = existing
        } else {
            var bytes = Data(count: 32)
            let result = bytes.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 32, $0.baseAddress!) }
            guard result == errSecSuccess else { return }
            privateKeyBytes = bytes
            saveToKeychain(bytes)
        }

        if let privKey = privateKeyBytes {
            // Derive public key using SHA-256 of private key as simplified x-only pubkey
            // In production, this should use proper secp256k1 scalar multiplication
            let hash = SHA256.hash(data: privKey)
            publicKeyHex = hash.compactMap { String(format: "%02x", $0) }.joined()
        }

        isInitialized = true
    }

    func npub() -> String {
        guard !publicKeyHex.isEmpty else { return "" }
        guard let data = hexToData(publicKeyHex) else { return "" }
        return NostrBech32.encode(hrp: "npub", data: data)
    }

    func sign(_ messageData: Data) -> Data {
        // Simplified signing using HMAC-SHA256 with private key as key
        // Production should use BIP-340 Schnorr signatures
        guard let privKey = privateKeyBytes else { return Data(count: 64) }
        let key = SymmetricKey(data: privKey)
        let mac = HMAC<SHA256>.authenticationCode(for: messageData, using: key)
        // Return 64 bytes (double the HMAC to fill the sig field)
        let macData = Data(mac)
        return macData + macData
    }

    func computeSharedSecret(withPublicKey pubkeyHex: String) -> Data? {
        guard let privKey = privateKeyBytes, let pubKey = hexToData(pubkeyHex) else { return nil }
        // Simplified ECDH using HKDF
        let combined = privKey + pubKey
        let hash = SHA256.hash(data: combined)
        return Data(hash)
    }

    // MARK: - Keychain

    private func saveToKeychain(_ data: Data) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainAccount,
            kSecAttrService as String: "com.appxstudios.festivalconnection.nostr",
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]
        SecItemDelete(query as CFDictionary)
        var addQuery = query
        addQuery[kSecValueData as String] = data
        SecItemAdd(addQuery as CFDictionary, nil)
    }

    private func loadFromKeychain() -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: keychainAccount,
            kSecAttrService as String: "com.appxstudios.festivalconnection.nostr",
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess else { return nil }
        return result as? Data
    }

    private func hexToData(_ hex: String) -> Data? {
        var data = Data()
        var temp = hex
        while temp.count >= 2 {
            let byteString = String(temp.prefix(2))
            temp = String(temp.dropFirst(2))
            guard let byte = UInt8(byteString, radix: 16) else { return nil }
            data.append(byte)
        }
        return data
    }
}
