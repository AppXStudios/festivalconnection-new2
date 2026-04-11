package com.appxstudios.festivalconnection.models

data class ChannelInfo(
    val id: String,
    var name: String,
    var creatorPublicKeyHex: String = "",
    var creatorDisplayName: String = "",
    var memberPublicKeys: MutableSet<String> = mutableSetOf(),
    var memberAvatarNames: MutableList<String> = mutableListOf(), // up to 4 display names
    var lastMessage: String = "",
    var lastMessageSenderName: String = "",
    var lastMessageTimestamp: Long? = null,
    var unreadCount: Int = 0,
    var isGeofenced: Boolean = true,
    var channelDescription: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var isVerified: Boolean = false,
    var isJoined: Boolean = false
) {
    val memberCount: Int get() = memberPublicKeys.size
}
