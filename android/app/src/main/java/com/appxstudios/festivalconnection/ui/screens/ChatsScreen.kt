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
import com.appxstudios.festivalconnection.ui.theme.*

@Composable
fun ChatsScreen(
    onChatSelected: (String, String) -> Unit = { _, _ -> }
) {
    var searchText by remember { mutableStateOf("") }
    var showNewChatSheet by remember { mutableStateOf(false) }

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
