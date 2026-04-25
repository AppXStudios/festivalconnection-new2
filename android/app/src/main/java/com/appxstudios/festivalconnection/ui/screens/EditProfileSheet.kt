package com.appxstudios.festivalconnection.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appxstudios.festivalconnection.ui.components.CircularAvatarComposable
import com.appxstudios.festivalconnection.ui.theme.*
import com.appxstudios.festivalconnection.ui.theme.GradientIcon
import com.google.gson.Gson

@Composable
fun EditProfileSheet(
    onDismiss: () -> Unit,
    context: Context
) {
    val prefs = remember { context.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE) }

    var displayName by remember { mutableStateOf(prefs.getString("fc_nickname", "") ?: "") }
    var handle by remember { mutableStateOf(prefs.getString("fc_handle", "") ?: "") }
    var aboutText by remember { mutableStateOf(prefs.getString("fc_about", "") ?: "") }
    var showCameraDialog by remember { mutableStateOf(false) }

    val handleRegex = remember { Regex("^[a-zA-Z0-9_]{1,30}$") }
    val originalHandle = remember { prefs.getString("fc_handle", "") ?: "" }
    val connectedPeerHandles = remember {
        val peerKeys = prefs.getStringSet("connected_peers", emptySet()) ?: emptySet()
        // Build set of known peer handles from stored peer data
        peerKeys.mapNotNull { key ->
            prefs.getString("peer_handle_$key", null)
        }.toSet()
    }
    val isHandleTaken = handle.trim().lowercase() != originalHandle.lowercase() &&
        connectedPeerHandles.any { it.lowercase() == handle.trim().lowercase() }
    val isFormValid = displayName.trim().isNotEmpty() && handleRegex.matches(handle) && !isHandleTaken

    if (showCameraDialog) {
        AlertDialog(
            onDismissRequest = { showCameraDialog = false },
            title = { Text("Change Profile Photo", color = Color.White) },
            text = { Text("Choose how to update your profile photo.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showCameraDialog = false }) {
                    Text("Take Photo", color = AccentPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCameraDialog = false }) {
                    Text("Choose from Library", color = AccentPink)
                }
            },
            containerColor = SurfaceDark
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar: Cancel / Save
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = AccentPink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            TextButton(
                onClick = {
                    val trimmedName = displayName.trim()
                    val trimmedHandle = handle.trim().replace("@", "")
                    prefs.edit()
                        .putString("fc_nickname", trimmedName)
                        .putString("fc_handle", trimmedHandle)
                        .putString("fc_about", aboutText)
                        .apply()

                    // Broadcast updated profile via Nostr kind-0 metadata.
                    // Use Gson to serialize so embedded quotes/newlines/backslashes in
                    // any field cannot produce malformed JSON.
                    val profileJson = Gson().toJson(mapOf(
                        "name" to trimmedName,
                        "display_name" to trimmedName,
                        "nip05" to trimmedHandle,
                        "about" to aboutText
                    ))
                    val metadataEvent = com.appxstudios.festivalconnection.mesh.nostr.NostrEvent.create(
                        kind = 0, content = profileJson
                    )
                    com.appxstudios.festivalconnection.mesh.nostr.NostrRelayManager.publishEvent(metadataEvent)

                    onDismiss()
                },
                enabled = isFormValid
            ) {
                Text(
                    text = "Save",
                    color = if (isFormValid) AccentPink else TextMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar with camera overlay
            Box(contentAlignment = Alignment.BottomEnd) {
                CircularAvatarComposable(
                    displayName = displayName,
                    size = 80.dp
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(AccentPink, CircleShape)
                        .clickable { showCameraDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Change photo",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // DISPLAY NAME section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "DISPLAY NAME",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                BasicTextField(
                    value = displayName,
                    onValueChange = { newValue ->
                        if (newValue.length <= 50) {
                            displayName = newValue
                        }
                    },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(AccentPink),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceMedium, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (displayName.isEmpty()) {
                                Text(
                                    text = "Display Name",
                                    color = TextMuted,
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // HANDLE section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "HANDLE",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceMedium, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    GradientText(
                        text = "@",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Normal
                    )
                    BasicTextField(
                        value = handle,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { it.isLetterOrDigit() || it == '_' }
                            if (filtered.length <= 30) {
                                handle = filtered
                            }
                        },
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(AccentPink),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        decorationBox = { innerTextField ->
                            Box {
                                if (handle.isEmpty()) {
                                    Text(
                                        text = "handle",
                                        color = TextMuted,
                                        fontSize = 16.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                if (isHandleTaken) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This handle is already taken by another user",
                        color = ErrorRed,
                        fontSize = 12.sp
                    )
                } else if (handle.isNotEmpty() && !handleRegex.matches(handle)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Letters, numbers, and underscores only (1-30 chars)",
                        color = ErrorRed,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ABOUT section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "ABOUT",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                BasicTextField(
                    value = aboutText,
                    onValueChange = { newValue ->
                        if (newValue.length <= 150) {
                            aboutText = newValue
                        }
                    },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(AccentPink),
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                        .background(SurfaceMedium, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (aboutText.isEmpty()) {
                                Text(
                                    text = "Tell people about yourself...",
                                    color = TextMuted,
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
