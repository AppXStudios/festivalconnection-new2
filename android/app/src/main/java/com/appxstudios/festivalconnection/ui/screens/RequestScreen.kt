package com.appxstudios.festivalconnection.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appxstudios.festivalconnection.ui.components.NumericKeypad
import com.appxstudios.festivalconnection.ui.theme.*

@Composable
fun RequestScreen(
    onCancel: () -> Unit = {},
    onCreateRequest: (amountSat: String, description: String) -> Unit = { _, _ -> }
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    // Display the entered digits as a sat-denominated amount.
    // Previously this divided by 100 and formatted as USD, but the value passed
    // downstream was always treated as sats by `WalletManager.createInvoice`,
    // so a "$5.00" display would mint a 500-sat (~$0.30) invoice. Showing sats
    // directly removes that whole unit-mismatch class of bugs.
    val parsedSats = remember(amount) { amount.toLongOrNull() ?: 0L }
    val displayAmount = remember(parsedSats) {
        if (parsedSats > 0) "$parsedSats sats" else "0 sats"
    }

    // Optional USD subtitle derived from the live wallet rate.
    val balanceSat by com.appxstudios.festivalconnection.services.WalletManager.balanceSat.collectAsState()
    val balanceUsd by com.appxstudios.festivalconnection.services.WalletManager.balanceUSD.collectAsState()
    val usdSubtitle = remember(parsedSats, balanceSat, balanceUsd) {
        if (parsedSats > 0 && balanceSat > 0 && balanceUsd > 0.0) {
            val usdPerSat = balanceUsd / balanceSat.toDouble()
            String.format(java.util.Locale.US, "≈ $%.2f USD", parsedSats * usdPerSat)
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar with Cancel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = AccentPink, fontSize = 17.sp)
            }
            Spacer(Modifier.weight(1f))
        }

        // Title
        Text(
            "Request (sats)",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 12.dp)
        )

        Spacer(Modifier.height(20.dp))

        // Sat-denominated amount display
        Text(
            text = displayAmount,
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold
        )

        if (usdSubtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = usdSubtitle,
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        // Description text field
        TextField(
            value = description,
            onValueChange = { description = it },
            placeholder = {
                Text("Description", color = TextMuted)
            },
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = AccentPink,
                focusedContainerColor = SurfaceMedium,
                unfocusedContainerColor = SurfaceMedium,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(20.dp))

        // Numeric keypad. Sats are integer-only — drop the decimal point even if the
        // shared NumericKeypad component renders one; the createInvoice() backend
        // expects a Long sat count.
        NumericKeypad(
            onDigit = { digit ->
                if (digit == ".") return@NumericKeypad
                amount += digit
            },
            onBackspace = {
                if (amount.isNotEmpty()) {
                    amount = amount.dropLast(1)
                }
            }
        )

        Spacer(Modifier.weight(1f))

        // Create Payment Request button with gradient background
        Button(
            onClick = { onCreateRequest(amount, description) },
            enabled = amount.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = if (amount.isNotEmpty()) {
                            mainGradientBrush()
                        } else {
                            Brush.linearGradient(listOf(TextMuted, TextMuted))
                        },
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Create Payment Request",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}
