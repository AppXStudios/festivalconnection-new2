package com.appxstudios.festivalconnection

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appxstudios.festivalconnection.mesh.ble.BLEMeshService
import com.appxstudios.festivalconnection.mesh.nostr.NostrEventDispatcher
import com.appxstudios.festivalconnection.mesh.nostr.NostrRelayManager
import com.appxstudios.festivalconnection.mesh.shared.PacketProcessor
import com.appxstudios.festivalconnection.security.IdentityManager
import com.appxstudios.festivalconnection.security.NostrIdentity
import com.appxstudios.festivalconnection.services.PermissionsManager
import com.appxstudios.festivalconnection.services.WalletManager
import com.appxstudios.festivalconnection.ui.screens.*
import com.appxstudios.festivalconnection.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var bleService: BLEMeshService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize identity FIRST (NostrIdentity + Ed25519 IdentityManager)
        // NostrIdentity must be initialized before NostrRelayManager.connect so that
        // all published events have a valid pubkey.
        NostrIdentity.initialize(this)
        IdentityManager.initialize(this)

        // Initialize backends
        PermissionsManager.initialize(this)
        NostrRelayManager.connect()
        // Install central dispatcher so multiple screens can observe incoming events
        NostrEventDispatcher.install()
        WalletManager.connect(this)

        // Instantiate BLE mesh transport (CrowdSync BLE layer)
        val appContext = applicationContext
        val prefs = appContext.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE)
        val nickname = prefs.getString("fc_nickname", null) ?: IdentityManager.defaultDisplayName
        val ble = BLEMeshService(this).apply {
            configure(IdentityManager.peerID(), nickname)
            onPacketReceived = { data ->
                PacketProcessor.receive(data, PacketProcessor.TransportType.BLE)
            }
            onPeerDiscovered = { peerHex, peerNickname ->
                val p = appContext.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE)
                val existing = p.getStringSet("connected_peers", mutableSetOf()) ?: mutableSetOf()
                if (!existing.contains(peerHex)) {
                    val updated = existing.toMutableSet().apply { add(peerHex) }
                    p.edit()
                        .putStringSet("connected_peers", updated)
                        .putString("peer_handle_$peerHex", peerNickname)
                        .apply()
                }
            }
            start()
        }
        bleService = ble

        // Wire PacketProcessor callbacks to persistence
        // When a mesh-layer message arrives, record the sender in "connected_peers" so the
        // Chats screen shows them even if they were never QR-scanned.
        PacketProcessor.onMessage = { senderHex, _, _ ->
            val p = appContext.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE)
            val existing = p.getStringSet("connected_peers", mutableSetOf()) ?: mutableSetOf()
            if (!existing.contains(senderHex)) {
                val updated = existing.toMutableSet().apply { add(senderHex) }
                val handle = p.getString("peer_handle_$senderHex", null)
                    ?: senderHex.take(8).lowercase()
                p.edit()
                    .putStringSet("connected_peers", updated)
                    .putString("peer_handle_$senderHex", handle)
                    .apply()
            }
        }
        PacketProcessor.onAnnounce = { peerHex, peerNickname ->
            val p = appContext.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE)
            val existing = p.getStringSet("connected_peers", mutableSetOf()) ?: mutableSetOf()
            val updated = existing.toMutableSet().apply { add(peerHex) }
            p.edit()
                .putStringSet("connected_peers", updated)
                .putString("peer_handle_$peerHex", peerNickname)
                .apply()
        }
        PacketProcessor.onPaymentNotification = { _, _, _ ->
            // Refresh wallet on incoming payment notification
            CoroutineScope(Dispatchers.IO).launch {
                WalletManager.refreshBalance()
                WalletManager.refreshTransactions()
            }
        }

        setContent {
            FestivalConnectionTheme {
                MainTabsScreen()
            }
        }
    }

    override fun onDestroy() {
        bleService?.stop()
        super.onDestroy()
    }
}

@Composable
fun MainTabsScreen() {
    var selectedTab by remember { mutableStateOf(FestivalTab.Chats) }
    var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }

    val context = LocalContext.current
    val walletCoroutineScope = rememberCoroutineScope()

    val screen = subScreen

    when (screen) {
        is SubScreen.Chat -> {
            ChatScreen(
                peerKey = screen.peerKey,
                peerName = screen.peerName,
                onBack = { subScreen = SubScreen.None }
            )
            return
        }
        is SubScreen.Pay -> {
            PayScreen(
                onCancel = { subScreen = SubScreen.None },
                onNext = { amount, description ->
                    subScreen = SubScreen.InvoiceScanner(amountCents = amount)
                }
            )
            return
        }
        is SubScreen.Request -> {
            RequestScreen(
                onCancel = { subScreen = SubScreen.None },
                onCreateRequest = { amount, description ->
                    walletCoroutineScope.launch {
                        try {
                            val amountSat = (amount.toDoubleOrNull() ?: 0.0).toLong()
                            val invoice = WalletManager.createInvoice(
                                amountSat = if (amountSat > 0) amountSat else 1000L,
                                description = description
                            )
                            subScreen = SubScreen.InvoiceDisplay(invoice)
                        } catch (e: Exception) {
                            subScreen = SubScreen.None
                        }
                    }
                }
            )
            return
        }
        is SubScreen.AddFunds -> {
            AddFundsScreen(
                onCancel = { subScreen = SubScreen.None },
                onInvoiceCreated = { invoice ->
                    subScreen = SubScreen.InvoiceDisplay(invoice)
                }
            )
            return
        }
        is SubScreen.TransactionHistory -> {
            val walletTransactions by WalletManager.transactions.collectAsState()
            TransactionHistoryScreen(
                transactions = walletTransactions,
                onDone = { subScreen = SubScreen.None }
            )
            return
        }
        is SubScreen.EditProfile -> {
            EditProfileSheet(
                onDismiss = { subScreen = SubScreen.None },
                context = context
            )
            return
        }
        is SubScreen.ChannelChat -> {
            ChannelChatScreen(
                channelId = screen.channelId,
                channelName = screen.channelName,
                memberCount = screen.memberCount,
                onBack = { subScreen = SubScreen.None }
            )
            return
        }
        is SubScreen.InvoiceScanner -> {
            InvoiceScannerScreen(
                onCancel = { subScreen = SubScreen.None },
                onInvoiceParsed = { invoice ->
                    subScreen = SubScreen.InvoiceDisplay(invoice)
                }
            )
            return
        }
        is SubScreen.InvoiceDisplay -> {
            InvoiceDisplayScreen(
                invoice = screen.invoice,
                onDone = { subScreen = SubScreen.None }
            )
            return
        }
        is SubScreen.None -> { /* show tabs */ }
    }

    Scaffold(
        containerColor = BackgroundBlack,
        bottomBar = {
            FestivalTabBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                FestivalTab.Chats -> ChatsScreen(
                    onChatSelected = { peerKey, peerName ->
                        subScreen = SubScreen.Chat(peerKey, peerName)
                    }
                )
                FestivalTab.Nearby -> NearbyScreen()
                FestivalTab.Channels -> ChannelsScreen(
                    onChannelSelected = { id, name, count ->
                        subScreen = SubScreen.ChannelChat(id, name, count)
                    }
                )
                FestivalTab.Wallet -> WalletHomeScreen(
                    onPay = { subScreen = SubScreen.Pay },
                    onRequest = { subScreen = SubScreen.Request },
                    onAddFunds = { subScreen = SubScreen.AddFunds },
                    onHistory = { subScreen = SubScreen.TransactionHistory }
                )
                FestivalTab.QR -> QRScreen()
                FestivalTab.Settings -> SettingsScreen(
                    onEditProfile = { subScreen = SubScreen.EditProfile },
                    onWallet = { selectedTab = FestivalTab.Wallet },
                    onCrowdSync = { selectedTab = FestivalTab.Nearby }
                )
            }
        }
    }
}

@Composable
fun FestivalTabBar(
    selectedTab: FestivalTab,
    onTabSelected: (FestivalTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundBlack)
    ) {
        // Top divider
        HorizontalDivider(
            color = SurfaceMedium,
            thickness = 0.5.dp,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FestivalTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                Column(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // GRADIENT ICON FOR SELECTED TAB
                    if (isSelected) {
                        GradientIcon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            size = 24.dp
                        )
                    } else {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = TextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // GRADIENT TEXT FOR SELECTED TAB
                    if (isSelected) {
                        GradientText(
                            text = tab.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal
                        )
                    } else {
                        Text(
                            text = tab.label,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

enum class FestivalTab(val icon: ImageVector, val label: String) {
    Chats(Icons.Filled.Forum, "Chats"),
    Nearby(Icons.Filled.CellTower, "Nearby"),
    Channels(Icons.Filled.Tag, "Channels"),
    Wallet(Icons.Filled.AccountBalanceWallet, "Wallet"),
    QR(Icons.Filled.QrCode, "QR"),
    Settings(Icons.Filled.Settings, "Settings")
}

sealed class SubScreen {
    data object None : SubScreen()
    data class Chat(val peerKey: String, val peerName: String) : SubScreen()
    data object Pay : SubScreen()
    data object Request : SubScreen()
    data object AddFunds : SubScreen()
    data object TransactionHistory : SubScreen()
    data object EditProfile : SubScreen()
    data class ChannelChat(val channelId: String, val channelName: String, val memberCount: Int) : SubScreen()
    data class InvoiceScanner(val amountCents: String = "") : SubScreen()
    data class InvoiceDisplay(val invoice: String) : SubScreen()
}
