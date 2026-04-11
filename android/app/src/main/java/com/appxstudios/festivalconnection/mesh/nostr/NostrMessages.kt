package com.appxstudios.festivalconnection.mesh.nostr

import com.google.gson.Gson
import com.google.gson.JsonParser

data class NostrFilter(
    val ids: List<String>? = null,
    val authors: List<String>? = null,
    val kinds: List<Int>? = null,
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
    val eTags: List<String>? = null,
    val pTags: List<String>? = null
) {
    fun toJson(): String {
        val map = mutableMapOf<String, Any>()
        ids?.let { map["ids"] = it }
        authors?.let { map["authors"] = it }
        kinds?.let { map["kinds"] = it }
        since?.let { map["since"] = it }
        until?.let { map["until"] = it }
        limit?.let { map["limit"] = it }
        eTags?.let { map["#e"] = it }
        pTags?.let { map["#p"] = it }
        return Gson().toJson(map)
    }
}

sealed class ClientMessage {
    data class Event(val event: NostrEvent) : ClientMessage()
    data class Req(val subscriptionId: String, val filters: List<NostrFilter>) : ClientMessage()
    data class Close(val subscriptionId: String) : ClientMessage()

    fun serialized(): String = when (this) {
        is Event -> "[\"EVENT\",${event.toJson()}]"
        is Req -> {
            val parts = mutableListOf("\"REQ\"", "\"$subscriptionId\"")
            filters.forEach { parts.add(it.toJson()) }
            "[${parts.joinToString(",")}]"
        }
        is Close -> "[\"CLOSE\",\"$subscriptionId\"]"
    }
}

sealed class RelayMessage {
    data class EventMsg(val subscriptionId: String, val event: NostrEvent) : RelayMessage()
    data class Ok(val eventId: String, val accepted: Boolean, val message: String) : RelayMessage()
    data class Eose(val subscriptionId: String) : RelayMessage()
    data class Closed(val subscriptionId: String, val message: String) : RelayMessage()
    data class Notice(val message: String) : RelayMessage()

    companion object {
        fun parse(json: String): RelayMessage? {
            return try {
                val array = JsonParser.parseString(json).asJsonArray
                val type = array[0].asString

                when (type) {
                    "EVENT" -> {
                        val subId = array[1].asString
                        val eventObj = array[2].asJsonObject
                        val event = NostrEvent(
                            id = eventObj["id"].asString,
                            pubkey = eventObj["pubkey"].asString,
                            createdAt = eventObj["created_at"].asLong,
                            kind = eventObj["kind"].asInt,
                            tags = eventObj["tags"].asJsonArray.map { tagArr ->
                                tagArr.asJsonArray.map { it.asString }
                            },
                            content = eventObj["content"].asString,
                            sig = eventObj["sig"].asString
                        )
                        EventMsg(subId, event)
                    }
                    "OK" -> Ok(
                        array[1].asString,
                        array[2].asBoolean,
                        if (array.size() > 3) array[3].asString else ""
                    )
                    "EOSE" -> Eose(array[1].asString)
                    "CLOSED" -> Closed(array[1].asString, if (array.size() > 2) array[2].asString else "")
                    "NOTICE" -> Notice(array[1].asString)
                    else -> null
                }
            } catch (e: Exception) { null }
        }
    }
}
