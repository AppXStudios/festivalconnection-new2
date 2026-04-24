package com.appxstudios.festivalconnection.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature

// Ed25519 identity manager — generates and stores the CrowdSync device keypair.
// Keys are stored in EncryptedSharedPreferences (AES-256-GCM master key).
// The fingerprint is SHA-256 of the public key, used for peer identification.

object IdentityManager {
    private const val PREFS_FILE = "fc_identity"
    private const val KEY_PRIVATE = "ed25519_private"
    private const val KEY_PUBLIC = "ed25519_public"

    private var keyPair: KeyPair? = null
    private var prefs: SharedPreferences? = null

    var isInitialized: Boolean = false
        private set

    var publicKeyHex: String = ""
        private set

    var fingerprint: String = ""
        private set

    val shortFingerprint: String
        get() = fingerprint.take(8)

    val defaultDisplayName: String
        get() = "Peer ${fingerprint.take(4).uppercase()}"

    val defaultHandle: String
        get() = fingerprint.take(8).lowercase()

    fun initialize(context: Context) {
        if (isInitialized) return

        prefs = getEncryptedPrefs(context)

        val storedPrivate = prefs?.getString(KEY_PRIVATE, null)
        val storedPublic = prefs?.getString(KEY_PUBLIC, null)

        if (storedPrivate != null && storedPublic != null) {
            // Restore existing keypair from stored bytes
            val privBytes = hexToBytes(storedPrivate)
            val pubBytes = hexToBytes(storedPublic)

            try {
                val keyFactory = java.security.KeyFactory.getInstance("Ed25519")
                val privKeySpec = java.security.spec.PKCS8EncodedKeySpec(privBytes)
                val pubKeySpec = java.security.spec.X509EncodedKeySpec(pubBytes)
                val privateKey = keyFactory.generatePrivate(privKeySpec)
                val publicKey = keyFactory.generatePublic(pubKeySpec)
                keyPair = KeyPair(publicKey, privateKey)

                publicKeyHex = storedPublic
                fingerprint = sha256Hex(pubBytes)
            } catch (e: Exception) {
                // Stored keys are corrupt or format incompatible — regenerate
                val kpg = KeyPairGenerator.getInstance("Ed25519")
                keyPair = kpg.generateKeyPair()
                val pubBytes2 = keyPair!!.public.encoded
                val privBytes2 = keyPair!!.private.encoded
                publicKeyHex = bytesToHex(pubBytes2)
                fingerprint = sha256Hex(pubBytes2)

                prefs?.edit()
                    ?.putString(KEY_PRIVATE, bytesToHex(privBytes2))
                    ?.putString(KEY_PUBLIC, publicKeyHex)
                    ?.apply()
            }
        } else {
            // Generate new Ed25519 keypair
            val kpg = KeyPairGenerator.getInstance("Ed25519")
            keyPair = kpg.generateKeyPair()
            val pubBytes = keyPair!!.public.encoded
            val privBytes = keyPair!!.private.encoded
            publicKeyHex = bytesToHex(pubBytes)
            fingerprint = sha256Hex(pubBytes)

            prefs?.edit()
                ?.putString(KEY_PRIVATE, bytesToHex(privBytes))
                ?.putString(KEY_PUBLIC, publicKeyHex)
                ?.apply()
        }

        // Set default nickname/handle if not already set
        val appPrefs = context.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE)
        if (appPrefs.getString("fc_nickname", null) == null) {
            appPrefs.edit()
                .putString("fc_nickname", defaultDisplayName)
                .putString("fc_handle", defaultHandle)
                .apply()
        }

        isInitialized = true
    }

    fun peerID(): ByteArray {
        val pubBytes = hexToBytes(publicKeyHex)
        return MessageDigest.getInstance("SHA-256").digest(pubBytes).take(8).toByteArray()
    }

    fun sign(data: ByteArray): ByteArray? {
        val kp = keyPair ?: return null
        val sig = Signature.getInstance("Ed25519")
        sig.initSign(kp.private)
        sig.update(data)
        return sig.sign()
    }

    fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return try {
            val keyFactory = java.security.KeyFactory.getInstance("Ed25519")
            val pubKeySpec = java.security.spec.X509EncodedKeySpec(publicKey)
            val pubKey = keyFactory.generatePublic(pubKeySpec)
            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(pubKey)
            sig.update(data)
            sig.verify(signature)
        } catch (e: Exception) {
            false
        }
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun sha256Hex(data: ByteArray): String {
        return bytesToHex(MessageDigest.getInstance("SHA-256").digest(data))
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
