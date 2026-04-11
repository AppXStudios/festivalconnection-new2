package com.appxstudios.festivalconnection.models

data class PeerInfo(
    val publicKeyHex: String,
    var displayName: String,
    var handle: String,
    var lastSeen: Long = System.currentTimeMillis(),
    var profileImageData: ByteArray? = null,
    var isConnected: Boolean = false,
    var isReachable: Boolean = false,
    var noisePublicKey: ByteArray? = null
) {
    val connectionQuality: String
        get() = when {
            isConnected -> "Nearby"
            isReachable -> "In range"
            else -> "Searching..."
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeerInfo) return false
        return publicKeyHex == other.publicKeyHex
    }

    override fun hashCode(): Int = publicKeyHex.hashCode()
}
