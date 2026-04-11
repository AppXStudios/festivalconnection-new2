import Foundation
import CryptoKit

// MARK: - Noise Protocol Framework Implementation
// Pattern: XX (mutual authentication)
// DH: Curve25519 (X25519)
// Cipher: ChaCha20-Poly1305
// Hash: SHA-256
// Full name: Noise_XX_25519_ChaChaPoly_SHA256
//
// Adapted from BitChat reference — cryptographic logic copied verbatim.

// MARK: - Constants

enum NoiseConstants {
    static let maxMessageSize = 65535
    static let maxHandshakeMessageSize = 2048
    static let sessionTimeout: TimeInterval = 86400
    static let maxMessagesPerSession: UInt64 = 1_000_000_000
    static let handshakeTimeout: TimeInterval = 60
    static let maxSessionsPerPeer = 3
    static let protocolName = "Noise_XX_25519_ChaChaPoly_SHA256"
}

enum NoiseRole {
    case initiator
    case responder
}

enum NoiseSessionState: Equatable {
    case uninitialized
    case handshaking
    case established
}

enum NoiseError: Error {
    case uninitializedCipher
    case invalidCiphertext
    case handshakeComplete
    case handshakeNotComplete
    case missingLocalStaticKey
    case missingKeys
    case invalidMessage
    case authenticationFailure
    case invalidPublicKey
    case replayDetected
    case nonceExceeded
}

// MARK: - Cipher State

final class NoiseCipherState {
    private var key: SymmetricKey?
    private var nonce: UInt64 = 0
    private var useExtractedNonce: Bool = false

    // Replay protection
    private static let replayWindowSize = 1024
    private static let replayWindowBytes = replayWindowSize / 8
    private var highestReceivedNonce: UInt64 = 0
    private var replayWindow: [UInt8] = Array(repeating: 0, count: replayWindowBytes)

    init() {}

    init(key: SymmetricKey, useExtractedNonce: Bool = false) {
        self.key = key
        self.useExtractedNonce = useExtractedNonce
    }

    func initializeKey(_ key: SymmetricKey) {
        self.key = key
        self.nonce = 0
    }

    func hasKey() -> Bool { key != nil }

    func encrypt(_ plaintext: Data) throws -> Data {
        guard let key = key else { throw NoiseError.uninitializedCipher }
        let nonceBytes = makeNonce(nonce)
        let sealedBox = try ChaChaPoly.seal(plaintext, using: key, nonce: ChaChaPoly.Nonce(data: nonceBytes))

        var result = Data()
        if useExtractedNonce {
            // Prepend 4-byte big-endian nonce
            let n32 = UInt32(nonce & 0xFFFFFFFF)
            result.append(UInt8((n32 >> 24) & 0xFF))
            result.append(UInt8((n32 >> 16) & 0xFF))
            result.append(UInt8((n32 >> 8) & 0xFF))
            result.append(UInt8(n32 & 0xFF))
        }
        result.append(sealedBox.ciphertext)
        result.append(sealedBox.tag)

        nonce += 1
        if nonce >= NoiseConstants.maxMessagesPerSession {
            throw NoiseError.nonceExceeded
        }
        return result
    }

    func decrypt(_ ciphertext: Data) throws -> Data {
        guard let key = key else { throw NoiseError.uninitializedCipher }

        let decryptNonce: UInt64
        let actualCiphertext: Data

        if useExtractedNonce {
            guard ciphertext.count >= 4 else { throw NoiseError.invalidCiphertext }
            var extracted: UInt64 = 0
            for i in 0..<4 {
                extracted = (extracted << 8) | UInt64(ciphertext[i])
            }
            decryptNonce = extracted
            actualCiphertext = ciphertext.dropFirst(4)

            // Replay check
            guard isValidNonce(decryptNonce) else { throw NoiseError.replayDetected }
        } else {
            decryptNonce = nonce
            actualCiphertext = ciphertext
        }

        guard actualCiphertext.count >= 16 else { throw NoiseError.invalidCiphertext }
        let tagStart = actualCiphertext.count - 16
        let encrypted = actualCiphertext.prefix(tagStart)
        let tag = actualCiphertext.suffix(16)

        let nonceBytes = makeNonce(decryptNonce)
        let sealedBox = try ChaChaPoly.SealedBox(
            nonce: ChaChaPoly.Nonce(data: nonceBytes),
            ciphertext: encrypted,
            tag: tag
        )
        let plaintext = try ChaChaPoly.open(sealedBox, using: key)

        if useExtractedNonce {
            markNonceAsSeen(decryptNonce)
        } else {
            nonce += 1
        }

        return plaintext
    }

    // 12-byte nonce: first 4 bytes zero, last 8 bytes little-endian UInt64
    private func makeNonce(_ n: UInt64) -> Data {
        var bytes = Data(count: 12)
        for i in 0..<8 {
            bytes[4 + i] = UInt8((n >> (i * 8)) & 0xFF)
        }
        return bytes
    }

    private func isValidNonce(_ receivedNonce: UInt64) -> Bool {
        let windowSize = UInt64(Self.replayWindowSize)
        if highestReceivedNonce >= windowSize && receivedNonce <= highestReceivedNonce - windowSize {
            return false
        }
        if receivedNonce > highestReceivedNonce { return true }
        let offset = Int(highestReceivedNonce - receivedNonce)
        let byteIndex = offset / 8
        let bitIndex = offset % 8
        return (replayWindow[byteIndex] & (1 << bitIndex)) == 0
    }

    private func markNonceAsSeen(_ receivedNonce: UInt64) {
        if receivedNonce > highestReceivedNonce {
            let shift = Int(receivedNonce - highestReceivedNonce)
            if shift >= Self.replayWindowSize {
                replayWindow = Array(repeating: 0, count: Self.replayWindowBytes)
            } else {
                for i in stride(from: Self.replayWindowBytes - 1, through: 0, by: -1) {
                    let src = i - shift / 8
                    var newByte: UInt8 = 0
                    if src >= 0 {
                        newByte = replayWindow[src] >> (shift % 8)
                        if src > 0 && shift % 8 != 0 {
                            newByte |= replayWindow[src - 1] << (8 - shift % 8)
                        }
                    }
                    replayWindow[i] = newByte
                }
            }
            highestReceivedNonce = receivedNonce
            replayWindow[0] |= 1
        } else {
            let offset = Int(highestReceivedNonce - receivedNonce)
            replayWindow[offset / 8] |= (1 << (offset % 8))
        }
    }
}

// MARK: - Symmetric State

final class NoiseSymmetricState {
    private var chainingKey: Data
    private var h: Data
    private let cipherState = NoiseCipherState()

    init(protocolName: String) {
        let nameData = protocolName.data(using: .utf8)!
        if nameData.count <= 32 {
            var padded = nameData
            padded.append(Data(repeating: 0, count: 32 - nameData.count))
            h = padded
        } else {
            h = Data(SHA256.hash(data: nameData))
        }
        chainingKey = h
    }

    func mixKey(_ inputKeyMaterial: Data) {
        let outputs = hkdf(chainingKey: chainingKey, inputKeyMaterial: inputKeyMaterial, numOutputs: 2)
        chainingKey = outputs[0]
        cipherState.initializeKey(SymmetricKey(data: outputs[1]))
    }

    func mixHash(_ data: Data) {
        var combined = h
        combined.append(data)
        h = Data(SHA256.hash(data: combined))
    }

    func encryptAndHash(_ plaintext: Data) throws -> Data {
        if cipherState.hasKey() {
            let ciphertext = try cipherState.encrypt(plaintext)
            mixHash(ciphertext)
            return ciphertext
        }
        mixHash(plaintext)
        return plaintext
    }

    func decryptAndHash(_ ciphertext: Data) throws -> Data {
        if cipherState.hasKey() {
            let plaintext = try cipherState.decrypt(ciphertext)
            mixHash(ciphertext)
            return plaintext
        }
        mixHash(ciphertext)
        return ciphertext
    }

    func split() -> (NoiseCipherState, NoiseCipherState) {
        let outputs = hkdf(chainingKey: chainingKey, inputKeyMaterial: Data(), numOutputs: 2)
        let c1 = NoiseCipherState(key: SymmetricKey(data: outputs[0]), useExtractedNonce: true)
        let c2 = NoiseCipherState(key: SymmetricKey(data: outputs[1]), useExtractedNonce: true)
        return (c1, c2)
    }

    func getHandshakeHash() -> Data { h }

    // HKDF using HMAC-SHA256 (verbatim from BitChat)
    private func hkdf(chainingKey: Data, inputKeyMaterial: Data, numOutputs: Int) -> [Data] {
        let tempKey = Data(HMAC<SHA256>.authenticationCode(for: inputKeyMaterial, using: SymmetricKey(data: chainingKey)))
        var outputs: [Data] = []
        var prev = Data()
        for i in 1...numOutputs {
            var input = prev
            input.append(UInt8(i))
            let output = Data(HMAC<SHA256>.authenticationCode(for: input, using: SymmetricKey(data: tempKey)))
            outputs.append(output)
            prev = output
        }
        return outputs
    }
}

// MARK: - Handshake State (XX Pattern)

final class NoiseHandshakeState {
    private let role: NoiseRole
    private var symmetricState: NoiseSymmetricState
    private var localEphemeral: Curve25519.KeyAgreement.PrivateKey?
    private let localStatic: Curve25519.KeyAgreement.PrivateKey
    private var remoteEphemeralPublic: Curve25519.KeyAgreement.PublicKey?
    private(set) var remoteStaticPublic: Curve25519.KeyAgreement.PublicKey?
    private var messageIndex = 0

    // XX pattern:
    // -> e
    // <- e, ee, s, es
    // -> s, se

    init(role: NoiseRole, localStatic: Curve25519.KeyAgreement.PrivateKey) {
        self.role = role
        self.localStatic = localStatic
        self.symmetricState = NoiseSymmetricState(protocolName: NoiseConstants.protocolName)
        symmetricState.mixHash(Data()) // prologue
    }

    var isComplete: Bool { messageIndex >= 3 }

    func writeMessage(payload: Data = Data()) throws -> Data {
        guard !isComplete else { throw NoiseError.handshakeComplete }
        var message = Data()

        switch (role, messageIndex) {
        case (.initiator, 0):
            // -> e
            let ephemeral = Curve25519.KeyAgreement.PrivateKey()
            localEphemeral = ephemeral
            let pubData = ephemeral.publicKey.rawRepresentation
            symmetricState.mixHash(pubData)
            message.append(pubData)

        case (.responder, 1):
            // <- e, ee, s, es
            let ephemeral = Curve25519.KeyAgreement.PrivateKey()
            localEphemeral = ephemeral
            let pubData = ephemeral.publicKey.rawRepresentation
            symmetricState.mixHash(pubData)
            message.append(pubData)
            // ee
            guard let re = remoteEphemeralPublic, let le = localEphemeral else { throw NoiseError.missingKeys }
            let ee = try le.sharedSecretFromKeyAgreement(with: re)
            symmetricState.mixKey(ee.withUnsafeBytes { Data($0) })
            // s
            let encryptedStatic = try symmetricState.encryptAndHash(localStatic.publicKey.rawRepresentation)
            message.append(encryptedStatic)
            // es
            let es = try localStatic.sharedSecretFromKeyAgreement(with: re)
            symmetricState.mixKey(es.withUnsafeBytes { Data($0) })

        case (.initiator, 2):
            // -> s, se
            let encryptedStatic = try symmetricState.encryptAndHash(localStatic.publicKey.rawRepresentation)
            message.append(encryptedStatic)
            // se
            guard let re = remoteEphemeralPublic else { throw NoiseError.missingKeys }
            let se = try localStatic.sharedSecretFromKeyAgreement(with: re)
            symmetricState.mixKey(se.withUnsafeBytes { Data($0) })

        default:
            throw NoiseError.invalidMessage
        }

        // Encrypt payload
        let encryptedPayload = try symmetricState.encryptAndHash(payload)
        message.append(encryptedPayload)

        messageIndex += 1
        return message
    }

    func readMessage(_ message: Data) throws -> Data {
        guard !isComplete else { throw NoiseError.handshakeComplete }
        var offset = 0

        func readBytes(_ count: Int) throws -> Data {
            guard offset + count <= message.count else { throw NoiseError.invalidMessage }
            let data = message[offset..<(offset + count)]
            offset += count
            return Data(data)
        }

        switch (role, messageIndex) {
        case (.responder, 0):
            // -> e
            let re = try readBytes(32)
            remoteEphemeralPublic = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: re)
            symmetricState.mixHash(re)

        case (.initiator, 1):
            // <- e, ee, s, es
            let re = try readBytes(32)
            remoteEphemeralPublic = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: re)
            symmetricState.mixHash(re)
            // ee
            guard let le = localEphemeral, let rep = remoteEphemeralPublic else { throw NoiseError.missingKeys }
            let ee = try le.sharedSecretFromKeyAgreement(with: rep)
            symmetricState.mixKey(ee.withUnsafeBytes { Data($0) })
            // s (encrypted)
            let encryptedStatic = try readBytes(32 + 16) // 32 key + 16 tag
            let remoteStaticData = try symmetricState.decryptAndHash(encryptedStatic)
            remoteStaticPublic = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: remoteStaticData)
            // es
            guard let rsp = remoteStaticPublic, let le2 = localEphemeral else { throw NoiseError.missingKeys }
            let es = try le2.sharedSecretFromKeyAgreement(with: rsp)
            symmetricState.mixKey(es.withUnsafeBytes { Data($0) })

        case (.responder, 2):
            // -> s, se
            let encryptedStatic = try readBytes(32 + 16)
            let remoteStaticData = try symmetricState.decryptAndHash(encryptedStatic)
            remoteStaticPublic = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: remoteStaticData)
            // se
            guard let le = localEphemeral, let rsp = remoteStaticPublic else { throw NoiseError.missingKeys }
            let se = try le.sharedSecretFromKeyAgreement(with: rsp)
            symmetricState.mixKey(se.withUnsafeBytes { Data($0) })

        default:
            throw NoiseError.invalidMessage
        }

        // Decrypt payload
        let remaining = message.suffix(from: offset)
        let payload = try symmetricState.decryptAndHash(Data(remaining))

        messageIndex += 1
        return payload
    }

    func split() -> (send: NoiseCipherState, receive: NoiseCipherState) {
        let (c1, c2) = symmetricState.split()
        return role == .initiator ? (c1, c2) : (c2, c1)
    }

    func getHandshakeHash() -> Data {
        symmetricState.getHandshakeHash()
    }
}

// MARK: - Noise Session

final class NoiseSession {
    let peerID: String
    private(set) var state: NoiseSessionState = .uninitialized
    private var handshake: NoiseHandshakeState?
    private var sendCipher: NoiseCipherState?
    private var receiveCipher: NoiseCipherState?
    private(set) var remoteStaticPublicKey: Curve25519.KeyAgreement.PublicKey?
    private(set) var handshakeHash: Data?
    private let queue = DispatchQueue(label: "fc.noise.session", attributes: .concurrent)

    init(peerID: String) {
        self.peerID = peerID
    }

    func startHandshake(localStatic: Curve25519.KeyAgreement.PrivateKey, role: NoiseRole) throws -> Data {
        return try queue.sync(flags: .barrier) {
            let hs = NoiseHandshakeState(role: role, localStatic: localStatic)
            self.handshake = hs
            self.state = .handshaking
            return try hs.writeMessage()
        }
    }

    func processHandshakeMessage(_ message: Data, localStatic: Curve25519.KeyAgreement.PrivateKey) throws -> Data? {
        return try queue.sync(flags: .barrier) {
            guard let hs = handshake else { throw NoiseError.handshakeNotComplete }
            let payload = try hs.readMessage(message)

            if hs.isComplete {
                let (send, recv) = hs.split()
                sendCipher = send
                receiveCipher = recv
                remoteStaticPublicKey = hs.remoteStaticPublic
                handshakeHash = hs.getHandshakeHash()
                state = .established
                handshake = nil
                return nil
            }

            // Need to send response
            let response = try hs.writeMessage(payload: Data())
            if hs.isComplete {
                let (send, recv) = hs.split()
                sendCipher = send
                receiveCipher = recv
                remoteStaticPublicKey = hs.remoteStaticPublic
                handshakeHash = hs.getHandshakeHash()
                state = .established
                handshake = nil
            }
            return response
        }
    }

    func encrypt(_ plaintext: Data) throws -> Data {
        try queue.sync {
            guard let cipher = sendCipher else { throw NoiseError.handshakeNotComplete }
            return try cipher.encrypt(plaintext)
        }
    }

    func decrypt(_ ciphertext: Data) throws -> Data {
        try queue.sync {
            guard let cipher = receiveCipher else { throw NoiseError.handshakeNotComplete }
            return try cipher.decrypt(ciphertext)
        }
    }

    var isEstablished: Bool {
        queue.sync { state == .established }
    }

    func reset() {
        queue.sync(flags: .barrier) {
            state = .uninitialized
            handshake = nil
            sendCipher = nil
            receiveCipher = nil
            remoteStaticPublicKey = nil
            handshakeHash = nil
        }
    }
}

// MARK: - Session Manager

final class NoiseSessionManager {
    static let shared = NoiseSessionManager()
    private var sessions: [String: NoiseSession] = [:]
    private let queue = DispatchQueue(label: "fc.noise.manager", attributes: .concurrent)

    func getOrCreateSession(for peerID: String) -> NoiseSession {
        queue.sync(flags: .barrier) {
            if let existing = sessions[peerID] { return existing }
            let session = NoiseSession(peerID: peerID)
            sessions[peerID] = session
            return session
        }
    }

    func removeSession(for peerID: String) {
        queue.sync(flags: .barrier) {
            sessions[peerID]?.reset()
            sessions.removeValue(forKey: peerID)
        }
    }

    func allEstablishedSessions() -> [NoiseSession] {
        queue.sync { sessions.values.filter { $0.isEstablished } }
    }
}
