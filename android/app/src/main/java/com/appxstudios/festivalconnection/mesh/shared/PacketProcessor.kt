package com.appxstudios.festivalconnection.mesh.shared

import com.appxstudios.festivalconnection.protocol.CrowdSyncBinaryProtocol
import com.appxstudios.festivalconnection.protocol.CrowdSyncPacket
import com.appxstudios.festivalconnection.protocol.MessageType
import com.appxstudios.festivalconnection.protocol.PaymentPacketSerializer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

// Central router that receives raw bytes from all transports (BLE, Nearby, Nostr),
// deduplicates by message ID, decrements TTL and relays, and routes to handlers.

object PacketProcessor {
    private val seenIDs = ConcurrentHashMap<String, Long>()
    private const val DEDUP_WINDOW_MS = 300_000L
    private const val MAX_CACHE_SIZE = 1000

    // Callbacks
    var onAnnounce: ((String, String) -> Unit)? = null       // peerKeyHex, nickname
    var onMessage: ((String, String, String) -> Unit)? = null // senderHex, recipientHex, content
    var onLeave: ((String) -> Unit)? = null
    var onPaymentRequest: ((String, Long, String, String) -> Unit)? = null
    var onPaymentNotification: ((ByteArray, Long, Byte) -> Unit)? = null

    enum class TransportType { BLE, NEARBY, NOSTR }

    fun receive(data: ByteArray, fromTransport: TransportType) {
        val msgID = computeMessageID(data)
        if (isDuplicate(msgID)) return

        val packet = CrowdSyncBinaryProtocol.decode(data) ?: return
        routePacket(packet)

        // Relay if TTL > 1
        if (packet.ttl > 1) {
            val relayed = packet.copy(ttl = packet.ttl - 1)
            val relayedData = CrowdSyncBinaryProtocol.encode(relayed) ?: return
            // Relay to other transports (excluding source)
            // BLE and Nearby transports handle their own broadcast
        }
    }

    private fun routePacket(packet: CrowdSyncPacket) {
        val senderHex = packet.senderID.joinToString("") { "%02x".format(it) }
        val type = MessageType.fromByte(packet.type) ?: return

        when (type) {
            MessageType.ANNOUNCE -> {
                val nickname = String(packet.payload, Charsets.UTF_8).ifEmpty {
                    "Peer ${senderHex.take(4).uppercase()}"
                }
                onAnnounce?.invoke(senderHex, nickname)
            }
            MessageType.MESSAGE -> {
                val content = String(packet.payload, Charsets.UTF_8)
                val recipientHex = packet.recipientID?.joinToString("") { "%02x".format(it) } ?: ""
                onMessage?.invoke(senderHex, recipientHex, content)
            }
            MessageType.LEAVE -> onLeave?.invoke(senderHex)
            MessageType.PAYMENT_REQUEST -> {
                PaymentPacketSerializer.decodePaymentRequest(packet.payload)?.let { (amt, inv, desc) ->
                    onPaymentRequest?.invoke(senderHex, amt, inv, desc)
                }
            }
            MessageType.PAYMENT_NOTIFICATION -> {
                if (packet.payload.size >= 41) {
                    val hash = packet.payload.sliceArray(0 until 32)
                    var amt = 0L
                    for (i in 32 until 40) amt = (amt shl 8) or (packet.payload[i].toLong() and 0xFF)
                    onPaymentNotification?.invoke(hash, amt, packet.payload[40])
                }
            }
            else -> { /* Handled by specialized subsystems */ }
        }
    }

    private fun computeMessageID(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    private fun isDuplicate(id: String): Boolean {
        val now = System.currentTimeMillis()
        if (seenIDs.containsKey(id) && (now - (seenIDs[id] ?: 0)) < DEDUP_WINDOW_MS) return true
        seenIDs[id] = now
        if (seenIDs.size > MAX_CACHE_SIZE) {
            val cutoff = now - DEDUP_WINDOW_MS
            seenIDs.entries.removeAll { it.value < cutoff }
        }
        return false
    }
}
