package com.appxstudios.festivalconnection.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalContext
import com.appxstudios.festivalconnection.models.PeerInfo
import com.appxstudios.festivalconnection.ui.theme.*

@Composable
fun ChatsScreen(
    onChatSelected: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }
    var showNewChatSheet by remember { mutableStateOf(false) }

    // Read conversations from SharedPreferences
    val prefs = remember { context.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE) }
    val peerKeys = remember { prefs.getStringSet("connected_peers", emptySet())?.toList() ?: emptyList() }
    val conversations = remember(peerKeys) {
        peerKeys.map { key ->
            val name = prefs.getString("peer_handle_$key", null) ?: "Peer ${key.take(4).uppercase()}"
            PeerInfo(publicKeyHex = key, displayName = name, handle = key.take(8), lastSeen = System.currentTimeMillis())
        }.filter { peer ->
            searchText.isEmpty() || peer.displayName.contains(searchText, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientText(
                "Festival Connection",
                modifier = Modifier.weight(1f),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = { showNewChatSheet = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(brush = mainGradientDiagonalBrush(), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Forum,
                    contentDescription = "New Chat",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(SurfaceMedium, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                decorationBox = { inner ->
                    if (searchText.isEmpty()) {
                        Text("Search", color = TextMuted, fontSize = 15.sp)
                    }
                    inner()
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (conversations.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forum,
                        contentDescription = "No chats",
                        tint = TextSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No conversations yet", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("People nearby will appear when connected", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(conversations) { peer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChatSelected(peer.publicKeyHex, peer.displayName) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularAvatarComposable(displayName = peer.displayName, size = 52.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(peer.displayName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("Tap to message", color = TextSecondary, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    if (showNewChatSheet) {
        NewChatSheet(
            onDismiss = { showNewChatSheet = false },
            onPeerSelected = { peerKey ->
                showNewChatSheet = false
                onChatSelected(peerKey, peerKey)
            }
        )
    }
}
