package com.appxstudios.festivalconnection.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appxstudios.festivalconnection.models.WalletTransaction
import com.appxstudios.festivalconnection.services.WalletManager
import com.appxstudios.festivalconnection.ui.theme.*
import com.appxstudios.festivalconnection.ui.theme.GradientIcon
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TransactionHistoryScreen(
    transactions: List<WalletTransaction> = emptyList(),
    onDone: () -> Unit = {}
) {
    // Refresh transactions from WalletManager on appear
    LaunchedEffect(Unit) {
        WalletManager.refreshTransactions()
    }

    // Fallback: if the passed list is empty, collect from WalletManager
    val walletTransactions by WalletManager.transactions.collectAsState()
    val effectiveTransactions = if (transactions.isEmpty()) walletTransactions else transactions

    // Group transactions by date label derived from timestamp
    val grouped = remember(effectiveTransactions) {
        effectiveTransactions.groupBy { tx -> formatDateLabel(tx.timestamp) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Top bar with Done
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDone) {
                Text("Done", color = AccentPink, fontSize = 17.sp)
            }
            Spacer(Modifier.weight(1f))
        }

        // Title
        Text(
            "Transaction History",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 16.dp)
        )

        if (effectiveTransactions.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "No transactions",
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No transactions yet",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            // Transaction list grouped by date
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                grouped.forEach { (dateLabel, txList) ->
                    // Date section header
                    item(key = "header_$dateLabel") {
                        Text(
                            text = dateLabel,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }

                    // Transaction items for this date
                    items(txList, key = { it.id }) { tx ->
                        TransactionRow(tx = tx)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: WalletTransaction) {
    val isReceived = tx.direction == "received"
    val directionColor = if (isReceived) PresenceGreen else AccentPink
    val directionIcon = if (isReceived) Icons.Filled.ArrowCircleDown else Icons.Filled.ArrowCircleUp
    val amountPrefix = if (isReceived) "+" else "-"

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Direction icon
            Icon(
                imageVector = directionIcon,
                contentDescription = tx.direction,
                tint = directionColor,
                modifier = Modifier.size(32.dp)
            )

            // Description and date
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = tx.description,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatDateLabel(tx.timestamp),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            // Amount
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%s$%.2f", amountPrefix, tx.amountUSD),
                    color = directionColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${tx.amountSat} sats",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun formatDateLabel(timestamp: Long): String {
    val cal = Calendar.getInstance()
    val today = cal.get(Calendar.DAY_OF_YEAR)
    val todayYear = cal.get(Calendar.YEAR)
    cal.timeInMillis = timestamp
    val txDay = cal.get(Calendar.DAY_OF_YEAR)
    val txYear = cal.get(Calendar.YEAR)
    return when {
        txYear == todayYear && txDay == today -> "Today"
        txYear == todayYear && txDay == today - 1 -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}
