package com.appxstudios.festivalconnection.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appxstudios.festivalconnection.models.ChannelInfo
import com.appxstudios.festivalconnection.mesh.nostr.NostrChannels
import com.appxstudios.festivalconnection.mesh.nostr.NostrRelayManager
import com.appxstudios.festivalconnection.ui.components.CircularAvatarComposable
import com.appxstudios.festivalconnection.ui.theme.*

@Composable
fun ChannelsScreen(
    onChannelSelected: (String, String, Int) -> Unit = { _, _, _ -> }
) {
    var searchText by remember { mutableStateOf("") }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    val channels = remember { mutableStateListOf<ChannelInfo>() }
    var channelName by remember { mutableStateOf("") }
    var channelDesc by remember { mutableStateOf("") }

    // Subscribe to kind-40 channel creation events for discovery
    LaunchedEffect(Unit) {
        val filter = NostrChannels.channelDiscoveryFilter()
        NostrRelayManager.subscribe(filter)

        val previousHandler = NostrRelayManager.onEvent
        NostrRelayManager.onEvent = { event ->
            previousHandler?.invoke(event)
            if (event.kind == 40) {
                val parsed = NostrChannels.parseChannelCreation(event)
                if (parsed != null) {
                    val (name, about, _) = parsed
                    val exists = channels.any { it.id == event.id }
                    if (!exists && name.isNotBlank()) {
                        channels.add(ChannelInfo(
                            id = event.id,
                            name = name,
                            channelDescription = about
                        ))
                    }
                }
            }
        }
    }

    val filteredChannels = channels.filter {
        searchText.isEmpty() || it.name.contains(searchText, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Channels",
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )

            // Notifications
            IconButton(onClick = { showNotificationsDialog = true }) {
                GradientIcon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Notifications",
                    size = 24.dp
                )
            }
        }

        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(SurfaceMedium, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, "Search", tint = TextMuted, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(AccentPink),
                decorationBox = { inner ->
                    if (searchText.isEmpty()) {
                        Text("Search", color = TextMuted, fontSize = 15.sp)
                    }
                    inner()
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Your Convos section
        Text(
            "Your Convos",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // All Channels section - GRADIENT TEXT APPLIED
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientText(
                "All Channels",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredChannels.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Tag,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No channels yet", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Create or join a channel to start chatting with groups",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Create Channel button - GRADIENT APPLIED
                    Button(
                        onClick = { showCreateSheet = true },
                        modifier = Modifier
                            .background(
                                brush = mainGradientDiagonalBrush(),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("Create", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredChannels) { channel ->
                    ChannelRow(
                        channel = channel,
                        onClick = { onChannelSelected(channel.id, channel.name, channel.memberCount) }
                    )
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateChannelBottomSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, desc ->
                // Publish kind-40 NIP-28 channel creation event
                val channelEvent = NostrChannels.createChannel(name, desc)
                NostrRelayManager.publishEvent(channelEvent)

                // Use the Nostr event ID as the channel ID
                channels.add(ChannelInfo(
                    id = channelEvent.id,
                    name = name,
                    channelDescription = desc
                ))
                showCreateSheet = false
            }
        )
    }
}

@Composable
private fun ChannelRow(channel: ChannelInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(AccentPink, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                channel.name.take(1).uppercase(),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (channel.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.Verified, "Verified", tint = AccentPink, modifier = Modifier.size(14.dp))
                }
            }
            channel.lastMessage?.let { msg ->
                val prefix = channel.lastMessageSenderName?.let { "$it: " } ?: ""
                Text(
                    "$prefix$msg",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text("${channel.memberCount}", color = TextSecondary, fontSize = 12.sp)
            Text("members", color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun StackedAvatars(names: List<String>, count: Int) {
    Box(modifier = Modifier.width((24 + (count - 1) * 16).dp).height(24.dp)) {
        names.take(count).forEachIndexed { index, name ->
            Box(modifier = Modifier.offset(x = (index * 16).dp)) {
                CircularAvatarComposable(displayName = name, size = 24.dp)
            }
        }
    }
}

@Composable
fun CreateChannelBottomSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var channelName by remember { mutableStateOf("") }
    var channelDesc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Channel", color = Color.White) },
        text = {
            Column {
                Text("CHANNEL NAME", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceMedium, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(AccentPink),
                    decorationBox = { inner ->
                        if (channelName.isEmpty()) {
                            Text("EDC Mainstage Meetup", color = TextMuted, fontSize = 16.sp)
                        }
                        inner()
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("DESCRIPTION", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = channelDesc,
                    onValueChange = { channelDesc = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceMedium, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(AccentPink),
                    decorationBox = { inner ->
                        if (channelDesc.isEmpty()) {
                            Text("Optional description", color = TextMuted, fontSize = 16.sp)
                        }
                        inner()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text("Broadcasts via CrowdSync\u2122", color = TextSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (channelName.isNotBlank()) {
                        onCreate(channelName, channelDesc)
                    }
                }
            ) {
                Text("Create", color = if (channelName.isNotBlank()) AccentPink else TextMuted)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark
    )
}
