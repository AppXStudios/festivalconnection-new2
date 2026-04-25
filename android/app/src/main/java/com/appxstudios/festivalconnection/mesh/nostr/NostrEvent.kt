package com.appxstudios.festivalconnection.mesh.nostr

import com.appxstudios.festivalconnection.security.NostrIdentity
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.security.MessageDigest

data class NostrEvent(
    val id: String,
    val pubkey: String,
    @SerializedName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String
) {
    companion object {
        private val gson = Gson()

        /**
         * NIP-01 canonical serialization: [0, pubkey, created_at, kind, tags, content].
         * Escapes per JSON spec: ", \, \b, \f, \n, \r, \t, and all control chars (< 0x20)
         * as \u00XX. Forward slash is NOT escaped. This matches iOS JSONSerialization with
         * .withoutEscapingSlashes, ensuring identical event IDs across platforms.
         */
        fun serialize(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String {
            val tagsJson = tags.joinToString(",") { tag ->
                "[" + tag.joinToString(",") { "\"${escapeJsonString(it)}\"" } + "]"
            }
            return "[0,\"${escapeJsonString(pubkey)}\",$createdAt,$kind,[$tagsJson],\"${escapeJsonString(content)}\"]"
        }

        fun computeId(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String {
            val serialized = serialize(pubkey, createdAt, kind, tags, content)
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(serialized.toByteArray(Charsets.UTF_8))
            return NostrIdentity.bytesToHex(hash)
        }

        fun create(kind: Int, content: String, tags: List<List<String>> = emptyList()): NostrEvent {
            val pubkey = NostrIdentity.publicKeyHex
            val createdAt = System.currentTimeMillis() / 1000
            val id = computeId(pubkey, createdAt, kind, tags, content)
            val idBytes = NostrIdentity.hexToBytes(id) ?: ByteArray(32)
            val sigBytes = NostrIdentity.sign(idBytes)
            val sig = NostrIdentity.bytesToHex(sigBytes)

            return NostrEvent(id, pubkey, createdAt, kind, tags, content, sig)
        }

        fun fromJson(json: String): NostrEvent? {
            return try { gson.fromJson(json, NostrEvent::class.java) } catch (e: Exception) { null }
        }

        /**
         * NIP-01 / RFC 8259 compliant string escaping for canonical JSON.
         * Escapes: " \ \b \f \n \r \t and all control characters (code < 0x20) as \u00XX.
         * Does NOT escape forward slash (/) — slashes pass through unchanged.
         */
        private fun escapeJsonString(s: String): String {
            val sb = StringBuilder(s.length + 8)
            for (c in s) {
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\b' -> sb.append("\\b")
                    '' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (c.code < 0x20) {
                            sb.append(String.format("\\u%04x", c.code))
                        } else {
                            sb.append(c)
                        }
                    }
                }
            }
            return sb.toString()
        }
    }

    fun verifyId(): Boolean {
        val computed = computeId(pubkey, createdAt, kind, tags, content)
        return computed == id
    }

    /**
     * Verify the BIP-340 Schnorr signature against the event ID and pubkey.
     * Mirrors iOS NostrEvent.verifySignature (using P256K.Schnorr.XonlyKey.isValid).
     * Returns false if signature is missing, malformed, or does not validate.
     */
    fun verifySignature(): Boolean {
        return try {
            val pubBytes = NostrIdentity.hexToBytes(pubkey) ?: return false
            if (pubBytes.size != 32) return false
            val sigBytes = NostrIdentity.hexToBytes(sig) ?: return false
            if (sigBytes.size != 64) return false
            val msgHash = NostrIdentity.hexToBytes(id) ?: return false
            if (msgHash.size != 32) return false
            NostrIdentity.verifyBIP340(pubBytes, msgHash, sigBytes)
        } catch (e: Exception) {
            false
        }
    }

    fun toJson(): String = gson.toJson(this)
}
