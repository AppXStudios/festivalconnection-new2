package com.appxstudios.festivalconnection.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object NostrIdentity {
    private const val PREFS_NAME = "fc_nostr_identity"
    private const val KEY_PRIVATE = "nostr_private_key"

    private var privateKeyBytes: ByteArray? = null
    var publicKeyHex: String = ""
        private set
    var isInitialized: Boolean = false
        private set

    fun initialize(context: Context) {
        val prefs = getEncryptedPrefs(context)
        val existingHex = prefs.getString(KEY_PRIVATE, null)

        if (existingHex != null && existingHex.length == 64) {
            privateKeyBytes = hexToBytes(existingHex)
        } else {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            privateKeyBytes = bytes
            prefs.edit().putString(KEY_PRIVATE, bytesToHex(bytes)).apply()
        }

        // Derive public key using SHA-256 of private key as simplified x-only pubkey
        // In production, use proper secp256k1 scalar multiplication via BouncyCastle
        privateKeyBytes?.let { privKey ->
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(privKey)
            publicKeyHex = bytesToHex(hash)
        }

        isInitialized = true
    }

    fun npub(): String {
        if (publicKeyHex.isEmpty()) return ""
        val data = hexToBytes(publicKeyHex) ?: return ""
        return NostrBech32.encode("npub", data)
    }

    fun sign(messageData: ByteArray): ByteArray {
        // Simplified signing using HMAC-SHA256
        // Production should use BIP-340 Schnorr signatures
        val privKey = privateKeyBytes ?: return ByteArray(64)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(privKey, "HmacSHA256"))
        val hmac = mac.doFinal(messageData)
        return hmac + hmac // 64 bytes
    }

    fun computeSharedSecret(withPublicKeyHex: String): ByteArray? {
        val privKey = privateKeyBytes ?: return null
        val pubKey = hexToBytes(withPublicKeyHex) ?: return null
        // Simplified ECDH using SHA-256
        val combined = privKey + pubKey
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(combined)
    }

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
