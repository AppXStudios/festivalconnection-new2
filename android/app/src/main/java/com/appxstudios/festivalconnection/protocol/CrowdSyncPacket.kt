package com.appxstudios.festivalconnection.protocol

// Binary packet structure matching iOS CrowdSyncPacket and BitChat wire format.
// Header v1 (14 bytes): [version:1][type:1][ttl:1][timestamp:8][flags:1][payloadLength:2]

data class CrowdSyncPacket(
    val version: Int = 1,
    val type: Int,
    val senderID: ByteArray,
    val recipientID: ByteArray? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: ByteArray,
    val signature: ByteArray? = null,
    var ttl: Int = 7
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CrowdSyncPacket) return false
        return version == other.version && type == other.type && timestamp == other.timestamp &&
            senderID.contentEquals(other.senderID)
    }
    override fun hashCode(): Int = timestamp.hashCode() + type
}

object CrowdSyncBinaryProtocol {
    const val V1_HEADER_SIZE = 14
    const val SENDER_ID_SIZE = 8
    const val RECIPIENT_ID_SIZE = 8
    const val SIGNATURE_SIZE = 64
    const val FLAG_HAS_RECIPIENT = 0x01
    const val FLAG_HAS_SIGNATURE = 0x02
    // Wire-format parity with iOS CrowdSyncBinaryProtocol.Flags.isCompressed
    // (Protocol/CrowdSyncPacket.swift:46). Reserved for forward compatibility —
    // neither platform emits or consumes compressed payloads yet, but receivers
    // need to recognize the flag bit so a future compressed-capable peer doesn't
    // get its packets rejected as malformed.
    const val FLAG_IS_COMPRESSED = 0x04

    fun encode(packet: CrowdSyncPacket): ByteArray? {
        val buf = mutableListOf<Byte>()
        buf.add(packet.version.toByte())
        buf.add(packet.type.toByte())
        buf.add(packet.ttl.toByte())

        for (shift in 56 downTo 0 step 8) {
            buf.add(((packet.timestamp shr shift) and 0xFF).toByte())
        }

        var flags = 0
        if (packet.recipientID != null) flags = flags or FLAG_HAS_RECIPIENT
        if (packet.signature != null) flags = flags or FLAG_HAS_SIGNATURE
        buf.add(flags.toByte())

        val payloadLen = packet.payload.size.coerceAtMost(65535)
        buf.add(((payloadLen shr 8) and 0xFF).toByte())
        buf.add((payloadLen and 0xFF).toByte())

        val sender = packet.senderID.copyOf(SENDER_ID_SIZE)
        buf.addAll(sender.toList())

        packet.recipientID?.let {
            val recipient = it.copyOf(RECIPIENT_ID_SIZE)
            buf.addAll(recipient.toList())
        }

        buf.addAll(packet.payload.toList())

        packet.signature?.let {
            buf.addAll(it.take(SIGNATURE_SIZE).toList())
        }

        return buf.toByteArray()
    }

    fun decode(data: ByteArray): CrowdSyncPacket? {
        if (data.size < V1_HEADER_SIZE + SENDER_ID_SIZE) return null
        var offset = 0

        val version = data[offset++].toInt() and 0xFF
        if (version != 1) return null
        val type = data[offset++].toInt() and 0xFF
        val ttl = data[offset++].toInt() and 0xFF

        var timestamp = 0L
        repeat(8) { timestamp = (timestamp shl 8) or (data[offset++].toLong() and 0xFF) }

        val flags = data[offset++].toInt() and 0xFF
        val hasRecipient = (flags and FLAG_HAS_RECIPIENT) != 0
        val hasSignature = (flags and FLAG_HAS_SIGNATURE) != 0

        val payloadLength = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        offset += 2

        if (offset + SENDER_ID_SIZE > data.size) return null
        val senderID = data.sliceArray(offset until offset + SENDER_ID_SIZE)
        offset += SENDER_ID_SIZE

        val recipientID = if (hasRecipient) {
            if (offset + RECIPIENT_ID_SIZE > data.size) return null
            val r = data.sliceArray(offset until offset + RECIPIENT_ID_SIZE)
            offset += RECIPIENT_ID_SIZE
            r
        } else null

        if (offset + payloadLength > data.size) return null
        val payload = data.sliceArray(offset until offset + payloadLength)
        offset += payloadLength

        val signature = if (hasSignature && offset + SIGNATURE_SIZE <= data.size) {
            val s = data.sliceArray(offset until offset + SIGNATURE_SIZE)
            offset += SIGNATURE_SIZE
            s
        } else null

        return CrowdSyncPacket(version, type, senderID, recipientID, timestamp, payload, signature, ttl)
    }
}
