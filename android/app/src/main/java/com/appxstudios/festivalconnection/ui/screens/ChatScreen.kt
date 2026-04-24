package com.appxstudios.festivalconnection.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appxstudios.festivalconnection.models.ChatMessage
import com.appxstudios.festivalconnection.mesh.nostr.NostrDM
import com.appxstudios.festivalconnection.mesh.nostr.NostrRelayManager
import com.appxstudios.festivalconnection.security.NostrIdentity
import com.appxstudios.festivalconnection.services.WalletManager
import com.appxstudios.festivalconnection.ui.components.CircularAvatarComposable
import com.appxstudios.festivalconnection.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(
    peerKey: String,
    peerName: String,
    onBack: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentAmount by remember { mutableStateOf("") }
    var paymentDescription by remember { mutableStateOf("") }

    if (showPaymentDialog) {
        AlertDialog(
            onDismissRequest = {
                showPaymentDialog = false
                paymentAmount = ""
                paymentDescription = ""
            },
            title = { Text("Send Payment Request", color = Color.White) },
            text = {
                Column {
                    Text("Send a payment request to $peerName", color = TextSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    BasicTextField(
                        value = paymentAmount,
                        onValueChange = { paymentAmount = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceMedium, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(AccentPink),
                        decorationBox = { inner ->
                            if (paymentAmount.isEmpty()) {
                                Text("Amount (sats)", color = TextMuted, fontSize = 16.sp)
                            }
                            inner()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    BasicTextField(
                        value = paymentDescription,
                        onValueChange = { paymentDescription = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceMedium, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(AccentPink),
                        decorationBox = { inner ->
                            if (paymentDescription.isEmpty()) {
                                Text("Description (optional)", color = TextMuted, fontSize = 16.sp)
                            }
                            inner()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amount = paymentAmount.toLongOrNull()
                        if (amount != null && amount > 0) {
                            messages.add(ChatMessage(
                                senderKey = NostrIdentity.publicKeyHex,
                                recipientKey = peerKey,
                                content = "",
                                isIncoming = false,
                                messageType = 0x10,
                                paymentAmount = amount,
                                paymentDescription = paymentDescription
                            ))

                            // Send payment request via Nostr DM
                            val reqText = "payment_request:$amount:${paymentDescription}"
                            val dmEvent = NostrDM.createDirectMessage(peerKey, reqText)
                            dmEvent?.let { NostrRelayManager.publishEvent(it) }

                            showPaymentDialog = false
                            paymentAmount = ""
                            paymentDescription = ""
                        }
                    }
                ) {
                    Text(
                        "Send Request",
                        color = if ((paymentAmount.toLongOrNull() ?: 0L) > 0L) AccentPink else TextMuted
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPaymentDialog = false
                        paymentAmount = ""
                        paymentDescription = ""
                    }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceDark
        )
    }

    Scaffold(
        containerColor = BackgroundBlack,
        topBar = { ChatTopBar(peerName, onBack) },
        bottomBar = {
            ChatInputBar(
                messageText = messageText,
                onMessageTextChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank()) {
                        val text = messageText.trim()
                        messages.add(ChatMessage(
                            senderKey = NostrIdentity.publicKeyHex,
                            recipientKey = peerKey,
                            content = text,
                            isIncoming = false
                        ))
                        messageText = ""

                        // Send via Nostr NIP-04 DM
                        val dmEvent = NostrDM.createDirectMessage(peerKey, text)
                        dmEvent?.let { NostrRelayManager.publishEvent(it) }

                        // Network delivery is handled by the NostrDM publish above
                    }
                },
                onPayment = { showPaymentDialog = true }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                when {
                    msg.messageType == 0x10 -> PaymentRequestBubble(msg)
                    msg.messageType == 0x11 -> PaymentNotificationBubble(msg)
                    !msg.isIncoming -> SentMessageBubble(msg)
                    else -> ReceivedMessageBubble(msg)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ChatTopBar(peerName: String, onBack: () -> Unit) {
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
        CircularAvatarComposable(displayName = peerName, size = 36.dp)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(peerName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Nostr", color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChatInputBar(
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onPayment: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundBlack)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Payment button
        IconButton(onClick = onPayment) {
            GradientIcon(
                imageVector = Icons.Filled.ArrowUpward,
                size = 24.dp
            )
        }

        // Message input
        BasicTextField(
            value = messageText,
            onValueChange = onMessageTextChange,
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

        // Send button - GRADIENT APPLIED
        IconButton(
            onClick = { if (messageText.isNotBlank()) onSend() },
            modifier = Modifier
                .size(36.dp)
                .background(
                    brush = if (messageText.isNotBlank()) mainGradientDiagonalBrush()
                    else Brush.linearGradient(listOf(TextMuted, TextMuted)),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Filled.Send,
                contentDescription = "Send",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SentMessageBubble(msg: ChatMessage) {
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
            Text(formatTimestamp(msg.timestamp), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun ReceivedMessageBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(SurfaceDark, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .padding(12.dp)
        ) {
            Text(msg.content, color = Color.White, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(formatTimestamp(msg.timestamp), color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PaymentRequestBubble(
    msg: ChatMessage,
    onPayNow: () -> Unit = {
        // Pay the incoming payment request using its invoice
        msg.paymentInvoice?.let { invoice ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    WalletManager.sendPayment(invoice, msg.paymentAmount)
                } catch (e: Exception) {
                    println("[Chat] Payment failed: ${e.message}")
                }
            }
        }
    }
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (!msg.isIncoming) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(SurfaceDark, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = AccentPink,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (!msg.isIncoming) "Payment Request Sent" else "Payment Request",
                    color = AccentPink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            msg.paymentAmount?.let { amount ->
                Text("\u20BF$amount sats", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            msg.paymentDescription?.let { desc ->
                if (desc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(desc, color = TextSecondary, fontSize = 14.sp)
                }
            }
            if (msg.isIncoming) {
                Spacer(modifier = Modifier.height(12.dp))
                // Pay Now button - kept as AccentPink since it maps to GradientCoral
                Button(
                    onClick = onPayNow,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pay Now", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(formatTimestamp(msg.timestamp), color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PaymentNotificationBubble(msg: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .background(SurfaceDark, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Paid",
                tint = PresenceGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Paid", color = PresenceGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            msg.paymentAmount?.let { amount ->
                Text(" \u20BF$amount sats", color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
