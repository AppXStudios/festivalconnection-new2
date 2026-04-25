package com.appxstudios.festivalconnection.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

object NostrIdentity {
    private const val PREFS_NAME = "fc_nostr_identity"
    private const val KEY_PRIVATE = "nostr_private_key"

    // secp256k1 curve order n
    private val SECP256K1_N = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16
    )

    private var privateKeyBytes: ByteArray? = null
    var publicKeyHex: String = ""
        private set
    var isInitialized: Boolean = false
        private set

    private val curveSpec: ECNamedCurveParameterSpec by lazy {
        ECNamedCurveTable.getParameterSpec("secp256k1")
    }

    fun initialize(context: Context) {
        // Idempotency guard — re-running initialize() would clobber the in-memory
        // privateKeyBytes / publicKeyHex even though it would always re-read the
        // same key from EncryptedSharedPreferences. Mirrors IdentityManager's pattern.
        if (isInitialized) return

        val prefs = getEncryptedPrefs(context)
        val existingHex = prefs.getString(KEY_PRIVATE, null)

        if (existingHex != null && existingHex.length == 64) {
            privateKeyBytes = hexToBytes(existingHex)
        } else {
            // Generate a valid secp256k1 private key (1 <= d < n)
            val bytes = generatePrivateKey()
            privateKeyBytes = bytes
            prefs.edit().putString(KEY_PRIVATE, bytesToHex(bytes)).apply()
        }

        // Derive real secp256k1 x-only public key via scalar multiplication P = d*G
        privateKeyBytes?.let { privKey ->
            publicKeyHex = bytesToHex(derivePublicKey(privKey))
        }

        isInitialized = true
    }

    fun npub(): String {
        if (publicKeyHex.isEmpty()) return ""
        val data = hexToBytes(publicKeyHex) ?: return ""
        return NostrBech32.encode("npub", data)
    }

    /**
     * BIP-340 Schnorr signature over secp256k1.
     * Input is the 32-byte message hash (Nostr event id). Output is a 64-byte signature.
     */
    fun sign(messageData: ByteArray): ByteArray {
        val privBytes = privateKeyBytes ?: return ByteArray(64)
        val n = SECP256K1_N

        var d = BigInteger(1, privBytes)
        if (d.signum() == 0 || d >= n) return ByteArray(64)

        // P = d*G; if P.y is odd, use d' = n - d so that P has even y
        val p = curveSpec.g.multiply(d).normalize()
        if (p.yCoord.toBigInteger().testBit(0)) {
            d = n.subtract(d)
        }
        val pxBytes = fixedLength32(p.xCoord.toBigInteger())
        val dBytes = fixedLength32(d)

        // Auxiliary randomness (32 bytes)
        val auxRand = ByteArray(32)
        SecureRandom().nextBytes(auxRand)

        // t = bytes(d) XOR tagged_hash("BIP0340/aux", auxRand)
        val t = xor(dBytes, taggedHash("BIP0340/aux", auxRand))

        // k0 = int(tagged_hash("BIP0340/nonce", t || bytes(P) || m)) mod n
        val nonceInput = t + pxBytes + messageData
        var k = BigInteger(1, taggedHash("BIP0340/nonce", nonceInput)).mod(n)
        if (k.signum() == 0) {
            // Extremely unlikely; caller should retry, but return a zero sig for safety.
            return ByteArray(64)
        }

        // R = k*G; if R.y is odd, k = n - k
        val r = curveSpec.g.multiply(k).normalize()
        if (r.yCoord.toBigInteger().testBit(0)) {
            k = n.subtract(k)
        }
        val rxBytes = fixedLength32(r.xCoord.toBigInteger())

        // e = int(tagged_hash("BIP0340/challenge", bytes(R) || bytes(P) || m)) mod n
        val challengeInput = rxBytes + pxBytes + messageData
        val e = BigInteger(1, taggedHash("BIP0340/challenge", challengeInput)).mod(n)

        // sig = bytes(R) || bytes((k + e*d) mod n)
        val s = k.add(e.multiply(d)).mod(n)
        return rxBytes + fixedLength32(s)
    }

    /**
     * secp256k1 ECDH shared secret.
     * Lifts x-only public key with the given parity (0x02 = even y, 0x03 = odd y),
     * computes d*P, and returns the X coordinate (32 bytes). NostrDM uses this
     * directly as the AES-256 key per bitchat's NIP-04 pattern (no extra SHA-256 wrap),
     * matching iOS behavior.
     *
     * Nostr pubkeys are x-only (32 bytes), but ECDH needs a compressed (33-byte) point.
     * Each X has TWO possible Y parities and they produce DIFFERENT shared secrets.
     * Outgoing messages use 0x02 deterministically; the recipient must try BOTH on
     * decrypt because only one parity matches the sender's actual key.
     */
    fun computeSharedSecret(withPublicKeyHex: String, parityByte: Byte = 0x02): ByteArray? {
        val privBytes = privateKeyBytes ?: return null
        val pubBytes = hexToBytes(withPublicKeyHex) ?: return null
        if (pubBytes.size != 32) return null

        return try {
            val d = BigInteger(1, privBytes)
            if (d.signum() == 0 || d >= SECP256K1_N) return null

            // Lift x-only public key with the requested parity prefix and decode.
            val pubPoint = curveSpec.curve.decodePoint(byteArrayOf(parityByte) + pubBytes)

            val shared = pubPoint.multiply(d).normalize()
            if (shared.isInfinity) return null
            fixedLength32(shared.xCoord.toBigInteger())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns both candidate NIP-04 shared secrets for an x-only pubkey: [evenY (0x02), oddY (0x03)].
     * Caller should attempt decryption with each and accept the one that yields valid UTF-8 plaintext.
     * Mirrors iOS NostrIdentity.computeSharedSecretsBothParities.
     */
    fun computeSharedSecretsBothParities(withPublicKeyHex: String): List<ByteArray> {
        val results = mutableListOf<ByteArray>()
        computeSharedSecret(withPublicKeyHex, 0x02)?.let { results.add(it) }
        computeSharedSecret(withPublicKeyHex, 0x03)?.let { results.add(it) }
        return results
    }

    /**
     * BIP-340 Schnorr signature verification.
     * Verifies a 64-byte signature `sig` against the 32-byte x-only public key `pubKeyXBytes`
     * over the 32-byte message hash `msg`. Returns true iff the signature is valid.
     * Used by NostrEvent.verifySignature() to reject forged events on the relay path.
     */
    fun verifyBIP340(pubKeyXBytes: ByteArray, msg: ByteArray, sig: ByteArray): Boolean {
        return try {
            if (pubKeyXBytes.size != 32 || msg.size != 32 || sig.size != 64) return false

            val n = SECP256K1_N
            // Field characteristic p for secp256k1
            val p = curveSpec.curve.field.characteristic

            // Lift x-only to even-y point P
            val xBig = BigInteger(1, pubKeyXBytes)
            if (xBig >= p) return false
            val pPoint = try {
                curveSpec.curve.decodePoint(byteArrayOf(0x02) + pubKeyXBytes).normalize()
            } catch (e: Exception) {
                return false
            }
            if (pPoint.isInfinity) return false

            // Parse R.x and s from sig
            val rBytes = sig.sliceArray(0 until 32)
            val sBytes = sig.sliceArray(32 until 64)
            val rBig = BigInteger(1, rBytes)
            val sBig = BigInteger(1, sBytes)
            if (rBig >= p || sBig >= n) return false

            // e = int(taggedHash("BIP0340/challenge", r || P.x || msg)) mod n
            val challenge = rBytes + pubKeyXBytes + msg
            val e = BigInteger(1, taggedHash("BIP0340/challenge", challenge)).mod(n)

            // R = s*G - e*P
            val sG = curveSpec.g.multiply(sBig)
            val eP = pPoint.multiply(e)
            val rPoint = sG.subtract(eP).normalize()

            if (rPoint.isInfinity) return false
            // y must be even
            if (rPoint.yCoord.toBigInteger().testBit(0)) return false
            rPoint.xCoord.toBigInteger() == rBig
        } catch (e: Exception) {
            false
        }
    }

    // MARK: - secp256k1 helpers

    private fun generatePrivateKey(): ByteArray {
        val rng = SecureRandom()
        while (true) {
            val candidate = ByteArray(32)
            rng.nextBytes(candidate)
            val d = BigInteger(1, candidate)
            if (d.signum() != 0 && d < SECP256K1_N) return candidate
        }
    }

    private fun derivePublicKey(privateKeyBytes: ByteArray): ByteArray {
        val d = BigInteger(1, privateKeyBytes)
        val q: ECPoint = curveSpec.g.multiply(d).normalize()
        return fixedLength32(q.xCoord.toBigInteger())
    }

    private fun taggedHash(tag: String, msg: ByteArray): ByteArray {
        val tagHash = sha256(tag.toByteArray(Charsets.UTF_8))
        return sha256(tagHash + tagHash + msg)
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun xor(a: ByteArray, b: ByteArray): ByteArray {
        require(a.size == b.size) { "xor input size mismatch" }
        return ByteArray(a.size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }
    }

    /** Left-pad (or trim) an unsigned integer to exactly 32 bytes big-endian. */
    private fun fixedLength32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        if (raw.size == 32) return raw
        if (raw.size == 33 && raw[0] == 0.toByte()) {
            // BigInteger sign byte
            return raw.copyOfRange(1, 33)
        }
        if (raw.size > 32) return raw.copyOfRange(raw.size - 32, raw.size)
        val out = ByteArray(32)
        System.arraycopy(raw, 0, out, 32 - raw.size, raw.size)
        return out
    }

    // MARK: - Storage

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
