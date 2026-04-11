package com.appxstudios.festivalconnection.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appxstudios.festivalconnection.models.ChannelMessage
import com.appxstudios.festivalconnection.mesh.nostr.NostrRelayManager
import com.appxstudios.festivalconnection.security.NostrIdentity
import com.appxstudios.festivalconnection.ui.components.CircularAvatarComposable
import com.appxstudios.festivalconnection.ui.theme.*

@Composable
fun ChannelChatScreen(
    channelId: String,
    channelName: String,
    memberCount: Int,
    onBack: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChannelMessage>() }
    val relayCount by NostrRelayManager.connectedRelayCount.collectAsState()
    val myKey = remember { NostrIdentity.publicKeyHex }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundBlack)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("Back", color = AccentPink)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channelName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (relayCount > 0) PresenceGreen else Color.Red,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$memberCount members", color = TextSecondary, fontSize = 12.sp)
                }
            }
            GradientIcon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Notifications",
                size = 24.dp
            )
        }

        // Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                val isMe = msg.senderPublicKeyHex == myKey
                if (isMe) {
                    SentChannelMessage(msg)
                } else {
                    ReceivedChannelMessage(msg)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundBlack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier
                    .weight(1f)
                    .background(SurfaceMedium, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(AccentPink),
                decorationBox = { innerTextField ->
                    if (messageText.isEmpty()) {
                        Text("Message", color = TextMuted, fontSize = 16.sp)
                    }
                    innerTextField()
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send button - GRADIENT ICON APPLIED
            IconButton(
                onClick = {
                    if (messageText.trim().isNotEmpty()) {
                        messages.add(ChannelMessage(
                            channelId = channelId,
                            senderPublicKeyHex = myKey,
                            senderDisplayName = "",
                            content = messageText.trim()
                        ))
                        messageText = ""
                    }
                }
            ) {
                if (messageText.trim().isNotEmpty()) {
                    GradientIcon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send",
                        size = 24.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SentChannelMessage(msg: ChannelMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(AccentPink, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(msg.content, color = Color.White, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp)),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ReceivedChannelMessage(msg: ChannelMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        CircularAvatarComposable(displayName = msg.senderDisplayName, size = 28.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .background(SurfaceDark, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .padding(12.dp)
        ) {
            GradientText(
                msg.senderDisplayName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(msg.content, color = Color.White, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp)),
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
