package com.appxstudios.festivalconnection.ui.screens

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.appxstudios.festivalconnection.models.PeerInfo
import com.appxstudios.festivalconnection.ui.components.CircularAvatarComposable
import com.appxstudios.festivalconnection.ui.theme.*
import com.appxstudios.festivalconnection.ui.theme.GradientIcon

@Composable
fun NewChatSheet(
    onDismiss: () -> Unit,
    onPeerSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val peers = remember {
        val prefs = context.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE)
        val peerKeys = prefs.getStringSet("connected_peers", emptySet()) ?: emptySet()
        mutableStateListOf<PeerInfo>().apply {
            addAll(peerKeys.map { key ->
                val handle = prefs.getString("peer_handle_$key", null) ?: key.take(8).lowercase()
                PeerInfo(
                    publicKeyHex = key,
                    displayName = handle,
                    handle = handle
                )
            })
        }
    }
    var searchText by remember { mutableStateOf("") }

    val filteredPeers = remember(searchText, peers.size) {
        if (searchText.isEmpty()) {
            peers.toList()
        } else {
            peers.filter {
                it.displayName.contains(searchText, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Top bar with cancel
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Cancel",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "New Chat",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.weight(1f))
            // Placeholder for symmetry
            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceMedium, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(AccentPink),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchText.isEmpty()) {
                            Text(
                                text = "Search",
                                color = TextMuted,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content: peers list or empty state
        if (filteredPeers.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredPeers) { peer ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPeerSelected(peer.publicKeyHex)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        CircularAvatarComposable(
                            displayName = peer.displayName,
                            size = 40.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = peer.displayName,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            // Empty state with pulsing animation
            Spacer(modifier = Modifier.weight(1f))

            EmptySearchState()

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EmptySearchState() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val gradientBrush = mainGradientBrush()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Pulsing ring
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .drawBehind {
                        drawCircle(
                            brush = gradientBrush,
                            radius = size.minDimension / 2f,
                            alpha = pulseAlpha,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
            )

            // Gradient icon
            Icon(
                imageVector = Icons.Default.CellTower,
                contentDescription = "Searching",
                tint = TextSecondary,
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Searching for nearby people...",
            color = TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}
