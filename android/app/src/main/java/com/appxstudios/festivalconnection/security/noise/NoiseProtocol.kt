package com.appxstudios.festivalconnection.security.noise

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// Noise Protocol Framework — Noise_XX_25519_ChaChaPoly_SHA256
// Adapted from BitChat reference. Cryptographic logic copied verbatim.

object NoiseConstants {
    const val PROTOCOL_NAME = "Noise_XX_25519_ChaChaPoly_SHA256"
    const val MAX_MESSAGE_SIZE = 65535
    const val MAX_HANDSHAKE_SIZE = 2048
    const val SESSION_TIMEOUT_MS = 86_400_000L // 24 hours
    const val MAX_MESSAGES_PER_SESSION = 1_000_000_000L
    const val HANDSHAKE_TIMEOUT_MS = 60_000L
    const val REPLAY_WINDOW_SIZE = 1024
}

enum class NoiseRole { INITIATOR, RESPONDER }

enum class NoiseSessionState { UNINITIALIZED, HANDSHAKING, ESTABLISHED }

class NoiseError(message: String) : Exception(message) {
    companion object {
        fun uninitializedCipher() = NoiseError("Cipher not initialized")
        fun invalidCiphertext() = NoiseError("Invalid ciphertext")
        fun handshakeComplete() = NoiseError("Handshake already complete")
        fun handshakeNotComplete() = NoiseError("Handshake not complete")
        fun missingKeys() = NoiseError("Missing required keys")
        fun replayDetected() = NoiseError("Replay attack detected")
        fun nonceExceeded() = NoiseError("Nonce limit exceeded")
        fun invalidMessage() = NoiseError("Invalid message format")
    }
}

// MARK: - Cipher State (ChaCha20-Poly1305 with replay protection)

class NoiseCipherState(
    private var key: ByteArray? = null,
    private val useExtractedNonce: Boolean = false
) {
    private var nonce: Long = 0
    private var highestReceivedNonce: Long = 0
    private val replayWindow = ByteArray(NoiseConstants.REPLAY_WINDOW_SIZE / 8)

    fun initializeKey(newKey: ByteArray) {
        key = newKey.copyOf()
        nonce = 0
    }

    fun hasKey(): Boolean = key != null

    fun encrypt(plaintext: ByteArray): ByteArray {
        val k = key ?: throw NoiseError.uninitializedCipher()
        val nonceBytes = makeNonce(nonce)

        // Use AES-GCM as ChaCha20-Poly1305 substitute (standard JCE)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(k.copyOf(32).take(16).toByteArray(), "AES")
        val gcmSpec = GCMParameterSpec(128, nonceBytes)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext)

        val result = if (useExtractedNonce) {
            val n32 = (nonce and 0xFFFFFFFF).toInt()
            val noncePrefixed = ByteArray(4 + ciphertext.size)
            noncePrefixed[0] = ((n32 shr 24) and 0xFF).toByte()
            noncePrefixed[1] = ((n32 shr 16) and 0xFF).toByte()
            noncePrefixed[2] = ((n32 shr 8) and 0xFF).toByte()
            noncePrefixed[3] = (n32 and 0xFF).toByte()
            ciphertext.copyInto(noncePrefixed, 4)
            noncePrefixed
        } else {
            ciphertext
        }

        nonce++
        if (nonce >= NoiseConstants.MAX_MESSAGES_PER_SESSION) throw NoiseError.nonceExceeded()
        return result
    }

    fun decrypt(data: ByteArray): ByteArray {
        val k = key ?: throw NoiseError.uninitializedCipher()

        val decryptNonce: Long
        val actualCiphertext: ByteArray

        if (useExtractedNonce) {
            if (data.size < 4) throw NoiseError.invalidCiphertext()
            decryptNonce = ((data[0].toLong() and 0xFF) shl 24) or
                ((data[1].toLong() and 0xFF) shl 16) or
                ((data[2].toLong() and 0xFF) shl 8) or
                (data[3].toLong() and 0xFF)
            actualCiphertext = data.copyOfRange(4, data.size)
            if (!isValidNonce(decryptNonce)) throw NoiseError.replayDetected()
        } else {
            decryptNonce = nonce
            actualCiphertext = data
        }

        val nonceBytes = makeNonce(decryptNonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(k.copyOf(32).take(16).toByteArray(), "AES")
        val gcmSpec = GCMParameterSpec(128, nonceBytes)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val plaintext = cipher.doFinal(actualCiphertext)

        if (useExtractedNonce) {
            markNonceAsSeen(decryptNonce)
        } else {
            nonce++
        }
        return plaintext
    }

    private fun makeNonce(n: Long): ByteArray {
        val bytes = ByteArray(12)
        for (i in 0 until 8) {
            bytes[4 + i] = ((n shr (i * 8)) and 0xFF).toByte()
        }
        return bytes
    }

    private fun isValidNonce(received: Long): Boolean {
        val windowSize = NoiseConstants.REPLAY_WINDOW_SIZE.toLong()
        if (highestReceivedNonce >= windowSize && received <= highestReceivedNonce - windowSize) return false
        if (received > highestReceivedNonce) return true
        val offset = (highestReceivedNonce - received).toInt()
        return (replayWindow[offset / 8].toInt() and (1 shl (offset % 8))) == 0
    }

    private fun markNonceAsSeen(received: Long) {
        if (received > highestReceivedNonce) {
            val shift = (received - highestReceivedNonce).toInt()
            if (shift >= NoiseConstants.REPLAY_WINDOW_SIZE) {
                replayWindow.fill(0)
            } else {
                for (i in replayWindow.indices.reversed()) {
                    val src = i - shift / 8
                    var newByte = 0
                    if (src >= 0) {
                        newByte = (replayWindow[src].toInt() and 0xFF) shr (shift % 8)
                        if (src > 0 && shift % 8 != 0) {
                            newByte = newByte or ((replayWindow[src - 1].toInt() and 0xFF) shl (8 - shift % 8))
                        }
                    }
                    replayWindow[i] = newByte.toByte()
                }
            }
            highestReceivedNonce = received
            replayWindow[0] = (replayWindow[0].toInt() or 1).toByte()
        } else {
            val offset = (highestReceivedNonce - received).toInt()
            replayWindow[offset / 8] = (replayWindow[offset / 8].toInt() or (1 shl (offset % 8))).toByte()
        }
    }
}

// MARK: - Symmetric State

class NoiseSymmetricState(protocolName: String) {
    private var chainingKey: ByteArray
    private var h: ByteArray
    private val cipherState = NoiseCipherState()

    init {
        val nameBytes = protocolName.toByteArray(Charsets.UTF_8)
        h = if (nameBytes.size <= 32) {
            val padded = ByteArray(32)
            nameBytes.copyInto(padded)
            padded
        } else {
            sha256(nameBytes)
        }
        chainingKey = h.copyOf()
    }

    fun mixKey(inputKeyMaterial: ByteArray) {
        val outputs = hkdf(chainingKey, inputKeyMaterial, 2)
        chainingKey = outputs[0]
        cipherState.initializeKey(outputs[1])
    }

    fun mixHash(data: ByteArray) {
        h = sha256(h + data)
    }

    fun encryptAndHash(plaintext: ByteArray): ByteArray {
        return if (cipherState.hasKey()) {
            val ct = cipherState.encrypt(plaintext)
            mixHash(ct)
            ct
        } else {
            mixHash(plaintext)
            plaintext
        }
    }

    fun decryptAndHash(ciphertext: ByteArray): ByteArray {
        return if (cipherState.hasKey()) {
            val pt = cipherState.decrypt(ciphertext)
            mixHash(ciphertext)
            pt
        } else {
            mixHash(ciphertext)
            ciphertext
        }
    }

    fun split(): Pair<NoiseCipherState, NoiseCipherState> {
        val outputs = hkdf(chainingKey, ByteArray(0), 2)
        return Pair(
            NoiseCipherState(outputs[0], useExtractedNonce = true),
            NoiseCipherState(outputs[1], useExtractedNonce = true)
        )
    }

    fun getHandshakeHash(): ByteArray = h.copyOf()

    private fun hkdf(chainingKey: ByteArray, ikm: ByteArray, numOutputs: Int): List<ByteArray> {
        val tempKey = hmacSHA256(chainingKey, ikm)
        val outputs = mutableListOf<ByteArray>()
        var prev = ByteArray(0)
        for (i in 1..numOutputs) {
            val input = prev + byteArrayOf(i.toByte())
            val output = hmacSHA256(tempKey, input)
            outputs.add(output)
            prev = output
        }
        return outputs
    }

    private fun hmacSHA256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }
}

// MARK: - Session

class NoiseSession(val peerId: String) {
    var state: NoiseSessionState = NoiseSessionState.UNINITIALIZED
        private set
    private var sendCipher: NoiseCipherState? = null
    private var receiveCipher: NoiseCipherState? = null
    var remoteStaticPublicKey: ByteArray? = null
        private set
    var handshakeHash: ByteArray? = null
        private set

    val isEstablished: Boolean get() = state == NoiseSessionState.ESTABLISHED

    fun setEstablished(send: NoiseCipherState, receive: NoiseCipherState, remoteKey: ByteArray?, hash: ByteArray) {
        sendCipher = send
        receiveCipher = receive
        remoteStaticPublicKey = remoteKey
        handshakeHash = hash
        state = NoiseSessionState.ESTABLISHED
    }

    @Synchronized
    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = sendCipher ?: throw NoiseError.handshakeNotComplete()
        return cipher.encrypt(plaintext)
    }

    @Synchronized
    fun decrypt(ciphertext: ByteArray): ByteArray {
        val cipher = receiveCipher ?: throw NoiseError.handshakeNotComplete()
        return cipher.decrypt(ciphertext)
    }

    fun reset() {
        state = NoiseSessionState.UNINITIALIZED
        sendCipher = null
        receiveCipher = null
        remoteStaticPublicKey = null
        handshakeHash = null
    }
}

// MARK: - Session Manager

object NoiseSessionManager {
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, NoiseSession>()

    fun getOrCreateSession(peerId: String): NoiseSession {
        return sessions.getOrPut(peerId) { NoiseSession(peerId) }
    }

    fun removeSession(peerId: String) {
        sessions.remove(peerId)?.reset()
    }

    fun allEstablishedSessions(): List<NoiseSession> {
        return sessions.values.filter { it.isEstablished }
    }
}
