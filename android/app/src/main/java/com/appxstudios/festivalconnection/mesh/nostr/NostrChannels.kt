package com.appxstudios.festivalconnection.mesh.nostr

import com.google.gson.Gson

object NostrChannels {
    private val gson = Gson()

    fun createChannel(name: String, about: String = "", picture: String = ""): NostrEvent {
        val content = gson.toJson(mapOf("name" to name, "about" to about, "picture" to picture))
        return NostrEvent.create(kind = 40, content = content)
    }

    fun sendChannelMessage(channelId: String, content: String, replyTo: String? = null): NostrEvent {
        val tags = mutableListOf(listOf("e", channelId, "", "root"))
        replyTo?.let { tags.add(listOf("e", it, "", "reply")) }
        return NostrEvent.create(kind = 42, content = content, tags = tags)
    }

    fun channelMessageFilter(channelId: String, since: Long? = null): NostrFilter {
        return NostrFilter(kinds = listOf(42), eTags = listOf(channelId), since = since, limit = 100)
    }

    fun channelDiscoveryFilter(since: Long? = null): NostrFilter {
        return NostrFilter(kinds = listOf(40), since = since, limit = 50)
    }

    fun parseChannelCreation(event: NostrEvent): Triple<String, String, String>? {
        if (event.kind != 40) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            val json = gson.fromJson(event.content, Map::class.java)
            Triple(
                json["name"] as? String ?: "",
                json["about"] as? String ?: "",
                json["picture"] as? String ?: ""
            )
        } catch (e: Exception) { null }
    }
}
