package com.appxstudios.festivalconnection.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appxstudios.festivalconnection.mesh.nostr.NostrRelayManager
import com.appxstudios.festivalconnection.ui.theme.*

@Composable
fun SettingsScreen(
    onEditProfile: () -> Unit = {},
    onWallet: () -> Unit = {},
    onCrowdSync: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE) }
    val displayName = remember { prefs.getString("fc_nickname", "Festival Goer") ?: "Festival Goer" }
    val rawHandle = remember { prefs.getString("fc_handle", "") ?: "" }
    val handle = if (rawHandle.isNotEmpty()) "@$rawHandle" else "@anonymous"
    val relayCount by NostrRelayManager.connectedRelayCount.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }
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
            Text("Settings", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
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
            Icon(Icons.Filled.Search, contentDescription = "Search", tint = TextMuted, modifier = Modifier.size(20.dp))
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

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Profile Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(16.dp))
                    .clickable { onEditProfile() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(SurfaceMedium, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        displayName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(handle, color = TextSecondary, fontSize = 14.sp)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ACCOUNT section - GRADIENT TEXT APPLIED
            GradientText(
                "ACCOUNT",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(16.dp))
            ) {
                SettingsRow(icon = Icons.Filled.AccountBalanceWallet, label = "Wallet", detail = "$0.00", onClick = onWallet)
                HorizontalDivider(color = SurfaceMedium)
                SettingsRow(icon = Icons.Filled.Language, label = "Nostr Relays", detail = "$relayCount connected")
                HorizontalDivider(color = SurfaceMedium)
                SettingsRow(icon = Icons.Filled.QrCode, label = "My QR Code")
                HorizontalDivider(color = SurfaceMedium)
                SettingsRow(icon = Icons.Filled.PersonAdd, label = "Invite Friends")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // CONNECTIONS section - GRADIENT TEXT APPLIED
            GradientText(
                "CONNECTIONS",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(16.dp))
            ) {
                SettingsRow(icon = Icons.Filled.CellTower, label = "CrowdSync\u2122", detail = if (relayCount > 0) "Active" else "Searching", onClick = onCrowdSync)
                HorizontalDivider(color = SurfaceMedium)
                SettingsRow(icon = Icons.Filled.Sync, label = "Message Sync", detail = "Synced")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // PRIVACY & SECURITY - GRADIENT TEXT APPLIED
            GradientText(
                "PRIVACY & SECURITY",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(16.dp))
            ) {
                SettingsRow(icon = Icons.Filled.PanTool, label = "Privacy", onClick = { showPrivacyDialog = true })
                HorizontalDivider(color = SurfaceMedium)
                SettingsRow(icon = Icons.Filled.Security, label = "Security", onClick = { showSecurityDialog = true })
                HorizontalDivider(color = SurfaceMedium)
                SettingsRow(icon = Icons.Filled.Notifications, label = "Notifications")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // GENERAL - GRADIENT TEXT APPLIED
            GradientText(
                "GENERAL",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(16.dp))
            ) {
                SettingsRow(icon = Icons.Filled.Storage, label = "Storage", onClick = { showStorageDialog = true })
                HorizontalDivider(color = SurfaceMedium)
                SettingsRow(icon = Icons.Filled.Star, label = "Rate App")
                HorizontalDivider(color = SurfaceMedium)
                SettingsRow(icon = Icons.Filled.Info, label = "About", onClick = { showAboutDialog = true })
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Version $versionName\nBuilt with CrowdSync\u2122 technology",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Dialogs
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy", color = Color.White) },
            text = { Text("Your messages are encrypted end-to-end using the Nostr protocol. Only you and your recipients can read them.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("OK", color = AccentPink) } },
            containerColor = SurfaceDark
        )
    }

    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = { Text("Security", color = Color.White) },
            text = { Text("Your private key is stored securely on this device. Never share your private key with anyone.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { showSecurityDialog = false }) { Text("OK", color = AccentPink) } },
            containerColor = SurfaceDark
        )
    }

    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text("Storage", color = Color.White) },
            text = { Text("Messages and media are stored locally on your device.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { showStorageDialog = false }) { Text("OK", color = AccentPink) } },
            containerColor = SurfaceDark
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Festival Connection", color = Color.White) },
            text = { Text("Festival Connection uses the Nostr protocol and CrowdSync\u2122 mesh networking to keep you connected at festivals, even without internet.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("OK", color = AccentPink) } },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    detail: String? = null,
    tint: Color = AccentPink,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // GRADIENT ICON APPLIED - all icons now use gradient
        GradientIcon(
            imageVector = icon,
            size = 22.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            label,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontSize = 16.sp
        )

        if (detail != null) {
            Text(detail, color = TextSecondary, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(4.dp))
        }

        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
