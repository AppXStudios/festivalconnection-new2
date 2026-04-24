import Foundation
import CryptoKit
import Security
import P256K

@MainActor
final class NostrIdentity: ObservableObject {
    static let shared = NostrIdentity()

    @Published var isInitialized = false
    @Published var publicKeyHex = ""

    // Raw 32-byte secp256k1 private scalar, used to build Schnorr / KeyAgreement keys on demand.
    private var privateKeyBytes: Data?
    private let keychainAccount = "fc_nostr_privkey"

    func initialize() {
        // Load existing key if present, otherwise generate a new one via the real secp256k1 library
        // so we end up with a valid scalar in [1, n-1] and a matching x-only public key.
        if let existing = loadFromKeychain(), existing.count == 32,
           let schnorrKey = try? P256K.Schnorr.PrivateKey(dataRepresentation: existing) {
            privateKeyBytes = existing
            publicKeyHex = Data(schnorrKey.xonly.bytes).hex
        } else if let schnorrKey = try? P256K.Schnorr.PrivateKey() {
            let priv = schnorrKey.dataRepresentation
            privateKeyBytes = priv
            publicKeyHex = Data(schnorrKey.xonly.bytes).hex
            saveToKeychain(priv)
        } else {
            // Extremely unlikely — secp256k1 key gen failed. Leave uninitialized.
            return
        }

        isInitialized = true
    }

    func npub() -> String {
        guard !publicKeyHex.isEmpty else { return "" }
        guard let data = hexToData(publicKeyHex) else { return "" }
        return NostrBech32.encode(hrp: "npub", data: data)
    }

    /// BIP-340 Schnorr signature over the given message.
    /// For NIP-01 events the message is the 32-byte SHA-256 of the serialized event,
    /// but this works for any message bytes.
    func sign(_ messageData: Data) -> Data {
        guard let privKey = privateKeyBytes,
              let schnorrKey = try? P256K.Schnorr.PrivateKey(dataRepresentation: privKey) else {
            return Data(count: 64)
        }

        var messageBytes = [UInt8](messageData)
        var auxRand = [UInt8](repeating: 0, count: 32)
        _ = auxRand.withUnsafeMutableBytes { ptr in
            SecRandomCopyBytes(kSecRandomDefault, 32, ptr.baseAddress!)
        }

        do {
            let sig = try schnorrKey.signature(message: &messageBytes, auxiliaryRand: &auxRand)
            return sig.dataRepresentation
        } catch {
            return Data(count: 64)
        }
    }

    /// NIP-04 shared secret: secp256k1 ECDH, returning only the 32-byte x-coordinate
    /// (used directly as the AES-256 key per NIP-04).
    func computeSharedSecret(withPublicKey pubkeyHex: String) -> Data? {
        guard let privKey = privateKeyBytes,
              let pubKeyData = hexToData(pubkeyHex) else { return nil }

        // Build a KeyAgreement private key from the same scalar we use for Schnorr.
        guard let keyAgreementPrivate = try? P256K.KeyAgreement.PrivateKey(dataRepresentation: privKey) else {
            return nil
        }

        // Nostr pubkeys are x-only (32 bytes). ECDH needs a compressed (33-byte) point,
        // so we try both Y parities (0x02 then 0x03).
        let candidatePoints: [Data]
        if pubKeyData.count == 32 {
            candidatePoints = [Data([0x02]) + pubKeyData, Data([0x03]) + pubKeyData]
        } else if pubKeyData.count == 33 {
            candidatePoints = [pubKeyData]
        } else {
            return nil
        }

        for point in candidatePoints {
            guard let keyAgreementPublic = try? P256K.KeyAgreement.PublicKey(
                dataRepresentation: point,
                format: .compressed
            ) else { continue }

            guard let sharedSecret = try? keyAgreementPrivate.sharedSecretFromKeyAgreement(
                with: keyAgreementPublic,
                format: .compressed
            ) else { continue }

            // Compressed output = [parity byte (0x02/0x03)] || [32-byte x-coord].
            // NIP-04 uses just the x-coordinate as the AES-256 key.
            let bytes = sharedSecret.withUnsafeBytes { Data($0) }
            guard bytes.count == 33 else { continue }
            return bytes.suffix(32)
        }

        return nil
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

// MARK: - Small helpers

private extension Data {
    var hex: String { map { String(format: "%02x", $0) }.joined() }
}
