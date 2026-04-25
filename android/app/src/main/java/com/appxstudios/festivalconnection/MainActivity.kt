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
import com.appxstudios.festivalconnection.mesh.nostr.NostrChannels
import com.appxstudios.festivalconnection.mesh.nostr.NostrEventDispatcher
import com.appxstudios.festivalconnection.mesh.nostr.NostrFilter
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var bleService: BLEMeshService? = null

    companion object {
        // Activity-scoped reference exposed so AppRoot composables can
        // re-call BLEMeshService.start() once permissions are granted.
        // start() is idempotent and silently no-ops if permissions are still missing.
        @Volatile
        var sharedBleService: BLEMeshService? = null
            private set
    }

    // Note: PermissionsScreen registers its own ActivityResult launcher via
    // rememberLauncherForActivityResult and drives the runtime-permissions flow itself.
    // No activity-scoped launcher is needed here.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize identity FIRST (NostrIdentity + Ed25519 IdentityManager)
        // NostrIdentity must be initialized before NostrRelayManager.connect so that
        // all published events have a valid pubkey.
        NostrIdentity.initialize(this)
        IdentityManager.initialize(this)

        // Initialize backends
        PermissionsManager.initialize(this)
        // Refresh permission state on launch so PermissionsScreen reflects current grants.
        PermissionsManager.getInstance().refreshAll()

        // Install central dispatcher BEFORE connecting so events arriving on the very
        // first relay frame are routed via NostrEventDispatcher. The default
        // NostrRelayManager.onEvent is null and would silently drop events that race
        // ahead of the install() call.
        NostrEventDispatcher.install()
        NostrRelayManager.connect()
        WalletManager.connect(this)

        // Subscribe globally to kind-42 channel messages and kind-40 channel
        // creation events at process start, so screens that observe the
        // dispatcher (NearbyScreen, ChannelsScreen) receive events even on a
        // cold launch directly into a tab whose own DisposableEffect has not
        // yet run. The per-screen subscriptions remain so screens can request
        // narrower filters when needed; duplicate REQs at the relay layer are
        // harmless.
        val nearbyFilter = NostrFilter(
            kinds = listOf(42),
            since = (System.currentTimeMillis() / 1000) - 3600,
            limit = 50
        )
        NostrRelayManager.subscribe(nearbyFilter)
        NostrRelayManager.subscribe(NostrChannels.channelDiscoveryFilter())

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
        sharedBleService = ble

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

        // Wire mesh relay callback. PacketProcessor.receive() decrements TTL and
        // hands the encoded packet here for forwarding. We re-broadcast on every
        // transport *except* the one that delivered the packet to avoid looping
        // a frame back to its source. Mirrors iOS PacketProcessor.relay()
        // (ios/FestivalConnection/Mesh/Shared/PacketProcessor.swift:84-92).
        PacketProcessor.onRelay = { relayedData, sourceTransport ->
            if (sourceTransport != PacketProcessor.TransportType.BLE) {
                bleService?.broadcast(relayedData)
            }
            // TODO: when Nearby / WiFiDirect transports are added, mirror the
            // BLE pattern here so packets fan out across all transports.
        }

        setContent {
            FestivalConnectionTheme {
                AppRoot()
            }
        }
    }

    override fun onDestroy() {
        bleService?.stop()
        super.onDestroy()
    }
}

/**
 * Onboarding-aware root composable that mirrors the iOS RootView flow:
 *  1. Show LaunchScreen for ~2 seconds
 *  2. If identity is not yet initialized, show SettingUpScreen
 *  3. If onboarding is not complete, show PermissionsScreen
 *  4. Else show MainTabsScreen
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE)
    }
    var showLaunch by remember { mutableStateOf(true) }
    var onboardingComplete by remember {
        mutableStateOf(prefs.getBoolean("fc_onboarding_complete", false))
    }
    // Track identity readiness reactively. NostrIdentity.initialize / IdentityManager.initialize
    // run synchronously in MainActivity.onCreate, so on first composition both are usually true.
    // We still poll briefly during the launch screen to cover any reinitialization edge cases.
    var identityReady by remember {
        mutableStateOf(NostrIdentity.isInitialized && IdentityManager.isInitialized)
    }

    LaunchedEffect(Unit) {
        // Hold the launch screen for ~2 seconds, matching iOS.
        delay(2000)
        identityReady = NostrIdentity.isInitialized && IdentityManager.isInitialized
        showLaunch = false
    }

    when {
        showLaunch -> LaunchScreen()

        !identityReady -> {
            SettingUpScreen(onInitialized = {
                identityReady = NostrIdentity.isInitialized && IdentityManager.isInitialized
            })
        }

        !onboardingComplete -> {
            PermissionsScreen(onGetStarted = {
                prefs.edit().putBoolean("fc_onboarding_complete", true).apply()
                onboardingComplete = true
                // BLEMeshService.start() in MainActivity.onCreate may have been a no-op
                // because BLE permissions were not yet granted. Re-trigger it now that
                // onboarding (which grants those permissions) is complete. start() is
                // idempotent — it checks permissions internally and silently returns
                // if still missing.
                MainActivity.sharedBleService?.start()
            })
        }

        else -> MainTabsScreen()
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
