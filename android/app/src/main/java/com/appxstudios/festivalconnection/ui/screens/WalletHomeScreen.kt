package com.appxstudios.festivalconnection.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.appxstudios.festivalconnection.models.WalletTransaction
import com.appxstudios.festivalconnection.services.WalletManager
import com.appxstudios.festivalconnection.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WalletHomeScreen(
    onPay: () -> Unit = {},
    onRequest: () -> Unit = {},
    onAddFunds: () -> Unit = {},
    onHistory: () -> Unit = {}
) {
    val balanceSat by WalletManager.balanceSat.collectAsState()
    val balanceUSD by WalletManager.balanceUSD.collectAsState()
    val transactions by WalletManager.transactions.collectAsState()
    val isConnected by WalletManager.isConnected.collectAsState()

    LaunchedEffect(Unit) {
        WalletManager.refreshBalance()
        WalletManager.refreshTransactions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Wallet",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Balance Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // YOUR BALANCE - GRADIENT TEXT APPLIED
            GradientText(
                "YOUR BALANCE",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            GradientText(
                "$${String.format(Locale.US, "%.2f", balanceUSD)}",
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "${balanceSat} sats",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Pay button - GRADIENT APPLIED
            Button(
                onClick = onPay,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        brush = mainGradientDiagonalBrush(),
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Text("Pay", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }

            // Request button - GRADIENT APPLIED
            Button(
                onClick = onRequest,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        brush = mainGradientDiagonalBrush(),
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Text("Request", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // RECENT ACTIVITY - GRADIENT TEXT APPLIED
        GradientText(
            "RECENT ACTIVITY",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (transactions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No transactions yet", color = TextSecondary, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Your payment history will appear here", color = TextSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn {
                items(transactions) { tx ->
                    TransactionRow(tx)
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: WalletTransaction) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (tx.direction == "received") Color(0xFF32D74B) else AccentPink

        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.description,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(tx.timestamp))
            Text(dateStr, color = TextSecondary, fontSize = 13.sp)
        }

        val sign = if (tx.direction == "received") "+" else "-"
        Text(
            "${sign}${tx.amountSat} sats",
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
