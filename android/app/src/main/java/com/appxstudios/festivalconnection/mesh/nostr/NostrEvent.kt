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

        fun serialize(pubkey: String, createdAt: Long, kind: Int, tags: List<List<String>>, content: String): String {
            val tagsJson = tags.joinToString(",") { tag ->
                "[" + tag.joinToString(",") { "\"${escapeJson(it)}\"" } + "]"
            }
            return "[0,\"$pubkey\",$createdAt,$kind,[$tagsJson],\"${escapeJson(content)}\"]"
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

        private fun escapeJson(str: String): String {
            val sb = StringBuilder()
            for (c in str) {
                when (c) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }
    }

    fun verifyId(): Boolean {
        val computed = computeId(pubkey, createdAt, kind, tags, content)
        return computed == id
    }

    fun toJson(): String = gson.toJson(this)
}
