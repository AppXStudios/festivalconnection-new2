import Foundation
import CryptoKit
import Security

/// Minimal BIP-39 mnemonic generator + validator for English wordlist.
///
/// Reference: https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki
///
/// - 128 bits of entropy → 12-word phrase (we generate 12 words)
/// - SHA-256 of the entropy contributes 4 bits of checksum
/// - 132 bits split into 12 chunks of 11 bits, each indexing the 2048-word list
enum BIP39 {

    /// Generates a fresh 12-word English mnemonic from 128 bits of CSPRNG entropy.
    /// Falls back to `arc4random_buf` only if `SecRandomCopyBytes` fails — both are
    /// kernel-backed CSPRNGs on Apple platforms, so this is purely defensive.
    static func generate12WordMnemonic() -> String {
        // 1. 128 bits (16 bytes) of cryptographically secure entropy
        var entropy = Data(count: 16)
        let status = entropy.withUnsafeMutableBytes { ptr -> Int32 in
            guard let base = ptr.baseAddress else { return errSecParam }
            return SecRandomCopyBytes(kSecRandomDefault, 16, base)
        }
        if status != errSecSuccess {
            // Defensive fallback — should never trigger on supported iOS versions.
            entropy.withUnsafeMutableBytes { ptr in
                if let base = ptr.baseAddress {
                    arc4random_buf(base, 16)
                }
            }
        }
        return mnemonic(from: entropy) ?? ""
    }

    /// Returns true if `phrase` is a valid BIP-39 English mnemonic of allowed length
    /// (12/15/18/21/24 words) AND its checksum verifies.
    static func isValidMnemonic(_ phrase: String) -> Bool {
        let words = phrase.trimmingCharacters(in: .whitespacesAndNewlines)
            .split(separator: " ", omittingEmptySubsequences: true)
            .map(String.init)
        let allowedLengths: Set<Int> = [12, 15, 18, 21, 24]
        guard allowedLengths.contains(words.count) else { return false }

        let wordlist = BIP39Wordlist.english
        guard wordlist.count == 2048 else { return false }
        // Build O(1) lookup
        var indexOf: [String: Int] = [:]
        indexOf.reserveCapacity(2048)
        for (i, w) in wordlist.enumerated() { indexOf[w] = i }

        // Concatenate 11-bit indices into a bitstring
        var bits: [Bool] = []
        bits.reserveCapacity(words.count * 11)
        for w in words {
            guard let idx = indexOf[w] else { return false }
            for shift in stride(from: 10, through: 0, by: -1) {
                bits.append(((idx >> shift) & 1) == 1)
            }
        }

        // Split into entropy + checksum
        let totalBits = bits.count
        let checksumBits = totalBits / 33
        let entropyBits = totalBits - checksumBits
        guard entropyBits % 8 == 0 else { return false }

        var entropyBytes = [UInt8](repeating: 0, count: entropyBits / 8)
        for i in 0..<entropyBits where bits[i] {
            entropyBytes[i / 8] |= UInt8(1 << (7 - (i % 8)))
        }

        let hash = SHA256.hash(data: Data(entropyBytes))
        let hashBytes = Array(hash)

        for i in 0..<checksumBits {
            let bit = ((hashBytes[i / 8] >> (7 - (i % 8))) & 1) == 1
            if bit != bits[entropyBits + i] { return false }
        }
        return true
    }

    // MARK: - Internal

    /// Converts entropy bytes (must be 16/20/24/28/32 bytes) into a mnemonic string.
    static func mnemonic(from entropy: Data) -> String? {
        let entropyBits = entropy.count * 8
        guard [128, 160, 192, 224, 256].contains(entropyBits) else { return nil }
        let checksumBits = entropyBits / 32
        let totalBits = entropyBits + checksumBits
        let wordCount = totalBits / 11

        // Compute checksum = first `checksumBits` bits of SHA-256(entropy)
        let hash = SHA256.hash(data: entropy)
        let hashBytes = Array(hash)

        // Build full bitstring
        var bits: [Bool] = []
        bits.reserveCapacity(totalBits)
        for byte in entropy {
            for shift in stride(from: 7, through: 0, by: -1) {
                bits.append(((byte >> shift) & 1) == 1)
            }
        }
        for i in 0..<checksumBits {
            bits.append(((hashBytes[i / 8] >> (7 - (i % 8))) & 1) == 1)
        }

        let wordlist = BIP39Wordlist.english
        guard wordlist.count == 2048 else { return nil }

        var words: [String] = []
        words.reserveCapacity(wordCount)
        for w in 0..<wordCount {
            var idx = 0
            for b in 0..<11 {
                idx = (idx << 1) | (bits[w * 11 + b] ? 1 : 0)
            }
            words.append(wordlist[idx])
        }
        return words.joined(separator: " ")
    }
}
