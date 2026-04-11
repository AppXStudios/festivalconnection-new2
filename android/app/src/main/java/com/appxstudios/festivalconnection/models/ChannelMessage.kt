package com.appxstudios.festivalconnection.models

import java.util.UUID

data class ChannelMessage(
    val id: String = UUID.randomUUID().toString(),
    val channelId: String,
    val senderPublicKeyHex: String,
    val senderDisplayName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
