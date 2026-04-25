package com.appxstudios.festivalconnection.mesh.nostr

import android.util.Base64
import com.appxstudios.festivalconnection.security.NostrIdentity
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object NostrDM {
    /**
     * Encrypt content per NIP-04. Outgoing messages always use even-Y (0x02) parity
     * for deterministic behaviour; the recipient will try both parities on decrypt.
     * Mirrors iOS NostrDM.encrypt.
     */
    fun encrypt(content: String, recipientPubkeyHex: String): String? {
        val sharedSecret = NostrIdentity.computeSharedSecret(recipientPubkeyHex, 0x02) ?: return null

        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(content.toByteArray(Charsets.UTF_8))

        val ciphertextB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        return "$ciphertextB64?iv=$ivB64"
    }

    /**
     * Decrypt content per NIP-04. Tries both Y parities (0x02 even, 0x03 odd) and returns
     * the first one that produces valid UTF-8 plaintext, since x-only pubkeys are ambiguous
     * and only one parity matches the sender's actual key. Mirrors iOS NostrDM.decrypt.
     */
    fun decrypt(encryptedContent: String, senderPubkeyHex: String): String? {
        val parts = encryptedContent.split("?iv=")
        if (parts.size != 2) return null

        val ciphertext = try { Base64.decode(parts[0], Base64.NO_WRAP) } catch (e: Exception) { return null }
        val iv = try { Base64.decode(parts[1], Base64.NO_WRAP) } catch (e: Exception) { return null }

        val candidateSecrets = NostrIdentity.computeSharedSecretsBothParities(senderPubkeyHex)
        if (candidateSecrets.isEmpty()) return null

        for (secret in candidateSecrets) {
            try {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"), IvParameterSpec(iv))
                val plaintext = cipher.doFinal(ciphertext)
                // Validate UTF-8 by attempting decode; mirrors iOS String(data:encoding:.utf8) check.
                val text = String(plaintext, Charsets.UTF_8)
                // Round-trip guard: if the decoded text re-encoded does not match raw bytes,
                // we got mojibake from the wrong parity — try the next secret.
                if (text.toByteArray(Charsets.UTF_8).contentEquals(plaintext)) {
                    return text
                }
            } catch (e: Exception) {
                // Wrong parity / padding mismatch — try the next candidate.
            }
        }
        return null
    }

    fun createDirectMessage(recipientPubkey: String, content: String): NostrEvent? {
        val encrypted = encrypt(content, recipientPubkey) ?: return null
        return NostrEvent.create(
            kind = 4,
            content = encrypted,
            tags = listOf(listOf("p", recipientPubkey))
        )
    }
}
