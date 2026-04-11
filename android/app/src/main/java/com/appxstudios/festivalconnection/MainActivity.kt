package com.appxstudios.festivalconnection

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
import com.appxstudios.festivalconnection.ui.screens.*
import com.appxstudios.festivalconnection.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FestivalConnectionTheme {
                MainTabsScreen()
            }
        }
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
                    subScreen = SubScreen.None
                }
            )
            return
        }
        is SubScreen.Request -> {
            RequestScreen(
                onCancel = { subScreen = SubScreen.None },
                onCreateRequest = { amount, description ->
                    walletCoroutineScope.launch {
                        subScreen = SubScreen.None
                    }
                }
            )
            return
        }
        is SubScreen.AddFunds -> {
            AddFundsScreen(
                onCancel = { subScreen = SubScreen.None }
            )
            return
        }
        is SubScreen.TransactionHistory -> {
            TransactionHistoryScreen(
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
