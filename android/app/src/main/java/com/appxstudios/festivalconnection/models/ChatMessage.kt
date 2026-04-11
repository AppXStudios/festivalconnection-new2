package com.appxstudios.festivalconnection.models

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderKey: String,
    val recipientKey: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean,
    var messageType: Int = 0x02,
    var paymentInvoice: String? = null,
    var paymentAmount: Long? = null,
    var paymentDescription: String? = null,
    var paymentHash: ByteArray? = null,
    var paymentDirection: Byte? = null,
    var paymentConfirmed: Boolean = false
)
