import Foundation
import CryptoKit
import CommonCrypto

enum NostrDM {
    /// Encrypt content per NIP-04
    @MainActor
    static func encrypt(content: String, recipientPubkeyHex: String) -> String? {
        guard let sharedSecret = NostrIdentity.shared.computeSharedSecret(withPublicKey: recipientPubkeyHex) else { return nil }

        let contentData = Data(content.utf8)

        // Generate random 16-byte IV
        var iv = Data(count: 16)
        let result = iv.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 16, $0.baseAddress!) }
        guard result == errSecSuccess else { return nil }

        // AES-256-CBC encrypt
        guard let encrypted = aes256CBCEncrypt(data: contentData, key: sharedSecret, iv: iv) else { return nil }

        let ciphertextB64 = encrypted.base64EncodedString()
        let ivB64 = iv.base64EncodedString()

        return "\(ciphertextB64)?iv=\(ivB64)"
    }

    /// Decrypt content per NIP-04
    @MainActor
    static func decrypt(encryptedContent: String, senderPubkeyHex: String) -> String? {
        let parts = encryptedContent.components(separatedBy: "?iv=")
        guard parts.count == 2,
              let ciphertext = Data(base64Encoded: parts[0]),
              let iv = Data(base64Encoded: parts[1]) else { return nil }

        guard let sharedSecret = NostrIdentity.shared.computeSharedSecret(withPublicKey: senderPubkeyHex) else { return nil }

        guard let decrypted = aes256CBCDecrypt(data: ciphertext, key: sharedSecret, iv: iv) else { return nil }

        return String(data: decrypted, encoding: .utf8)
    }

    /// Create a kind-4 encrypted DM event
    @MainActor
    static func createDirectMessage(to recipientPubkey: String, content: String) -> NostrEvent? {
        guard let encrypted = encrypt(content: content, recipientPubkeyHex: recipientPubkey) else { return nil }
        return NostrEvent.create(
            kind: 4,
            content: encrypted,
            tags: [["p", recipientPubkey]]
        )
    }

    // MARK: - AES-256-CBC

    private static func aes256CBCEncrypt(data: Data, key: Data, iv: Data) -> Data? {
        let bufferSize = data.count + kCCBlockSizeAES128
        var buffer = Data(count: bufferSize)
        var numBytesEncrypted = 0

        let status = buffer.withUnsafeMutableBytes { bufferPtr in
            data.withUnsafeBytes { dataPtr in
                key.withUnsafeBytes { keyPtr in
                    iv.withUnsafeBytes { ivPtr in
                        CCCrypt(
                            CCOperation(kCCEncrypt),
                            CCAlgorithm(kCCAlgorithmAES),
                            CCOptions(kCCOptionPKCS7Padding),
                            keyPtr.baseAddress, key.count,
                            ivPtr.baseAddress,
                            dataPtr.baseAddress, data.count,
                            bufferPtr.baseAddress, bufferSize,
                            &numBytesEncrypted
                        )
                    }
                }
            }
        }

        guard status == kCCSuccess else { return nil }
        return buffer.prefix(numBytesEncrypted)
    }

    private static func aes256CBCDecrypt(data: Data, key: Data, iv: Data) -> Data? {
        let bufferSize = data.count + kCCBlockSizeAES128
        var buffer = Data(count: bufferSize)
        var numBytesDecrypted = 0

        let status = buffer.withUnsafeMutableBytes { bufferPtr in
            data.withUnsafeBytes { dataPtr in
                key.withUnsafeBytes { keyPtr in
                    iv.withUnsafeBytes { ivPtr in
                        CCCrypt(
                            CCOperation(kCCDecrypt),
                            CCAlgorithm(kCCAlgorithmAES),
                            CCOptions(kCCOptionPKCS7Padding),
                            keyPtr.baseAddress, key.count,
                            ivPtr.baseAddress,
                            dataPtr.baseAddress, data.count,
                            bufferPtr.baseAddress, bufferSize,
                            &numBytesDecrypted
                        )
                    }
                }
            }
        }

        guard status == kCCSuccess else { return nil }
        return buffer.prefix(numBytesDecrypted)
    }
}
