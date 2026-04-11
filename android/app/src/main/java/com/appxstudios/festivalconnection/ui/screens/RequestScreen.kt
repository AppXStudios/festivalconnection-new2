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
    onCreateRequest: (amountCents: String, description: String) -> Unit = { _, _ -> }
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val displayAmount = remember(amount) {
        val value = amount.toDoubleOrNull() ?: 0.0
        "$%.2f".format(value / 100.0)
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
            "Request",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 12.dp)
        )

        Spacer(Modifier.height(20.dp))

        // Dollar amount display
        Text(
            text = displayAmount,
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold
        )

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

        // Numeric keypad
        NumericKeypad(
            onDigit = { digit ->
                if (digit == ".") {
                    if (!amount.contains(".")) {
                        amount += digit
                    }
                } else {
                    amount += digit
                }
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
