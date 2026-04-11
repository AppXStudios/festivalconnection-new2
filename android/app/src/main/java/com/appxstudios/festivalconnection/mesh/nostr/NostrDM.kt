package com.appxstudios.festivalconnection.mesh.nostr

import android.util.Base64
import com.appxstudios.festivalconnection.security.NostrIdentity
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object NostrDM {
    fun encrypt(content: String, recipientPubkeyHex: String): String? {
        val sharedSecret = NostrIdentity.computeSharedSecret(recipientPubkeyHex) ?: return null

        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(content.toByteArray(Charsets.UTF_8))

        val ciphertextB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        return "$ciphertextB64?iv=$ivB64"
    }

    fun decrypt(encryptedContent: String, senderPubkeyHex: String): String? {
        val parts = encryptedContent.split("?iv=")
        if (parts.size != 2) return null

        val ciphertext = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)

        val sharedSecret = NostrIdentity.computeSharedSecret(senderPubkeyHex) ?: return null

        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sharedSecret, "AES"), IvParameterSpec(iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) { null }
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
