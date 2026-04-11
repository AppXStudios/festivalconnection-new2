package com.appxstudios.festivalconnection.protocol

// Message types — byte values match iOS and BitChat exactly for cross-platform compatibility
enum class MessageType(val value: Int) {
    ANNOUNCE(0x01),
    MESSAGE(0x02),
    LEAVE(0x03),
    NOISE_HANDSHAKE(0x10),
    NOISE_ENCRYPTED(0x11),
    FRAGMENT(0x20),
    REQUEST_SYNC(0x21),
    FILE_TRANSFER(0x22),
    PAYMENT_REQUEST(0x30),
    PAYMENT_NOTIFICATION(0x31);

    companion object {
        fun fromByte(value: Int): MessageType? = entries.find { it.value == value }
    }
}

enum class NoisePayloadType(val value: Int) {
    PRIVATE_MESSAGE(0x01),
    READ_RECEIPT(0x02),
    DELIVERED(0x03),
    VERIFY_CHALLENGE(0x10),
    VERIFY_RESPONSE(0x11);
}

object PaymentPacketSerializer {
    fun encodePaymentRequest(invoice: String, amountSat: Long, description: String): ByteArray {
        val buf = mutableListOf<Byte>()
        for (shift in 56 downTo 0 step 8) {
            buf.add(((amountSat shr shift) and 0xFF).toByte())
        }
        buf.addAll(invoice.toByteArray(Charsets.UTF_8).toList())
        buf.add(0x00)
        buf.addAll(description.toByteArray(Charsets.UTF_8).toList())
        return buf.toByteArray()
    }

    fun decodePaymentRequest(data: ByteArray): Triple<Long, String, String>? {
        if (data.size < 9) return null
        var amountSat = 0L
        for (i in 0 until 8) {
            amountSat = (amountSat shl 8) or (data[i].toLong() and 0xFF)
        }
        val remaining = data.drop(8)
        val nullIdx = remaining.indexOf(0x00.toByte())
        if (nullIdx < 0) return null
        val invoice = String(remaining.take(nullIdx).toByteArray(), Charsets.UTF_8)
        val desc = String(remaining.drop(nullIdx + 1).toByteArray(), Charsets.UTF_8)
        return Triple(amountSat, invoice, desc)
    }

    fun encodePaymentNotification(paymentHash: ByteArray, amountSat: Long, direction: Byte): ByteArray {
        val buf = ByteArray(41)
        paymentHash.copyInto(buf, 0, 0, minOf(paymentHash.size, 32))
        for (shift in 56 downTo 0 step 8) {
            buf[32 + (56 - shift) / 8] = ((amountSat shr shift) and 0xFF).toByte()
        }
        buf[40] = direction
        return buf
    }
}
