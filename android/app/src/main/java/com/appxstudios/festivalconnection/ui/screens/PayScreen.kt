package com.appxstudios.festivalconnection.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appxstudios.festivalconnection.ui.theme.*

@Composable
fun PayScreen(
    onCancel: () -> Unit = {},
    onNext: (amountCents: String, description: String) -> Unit = { _, _ -> }
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val isValid = remember(amount) {
        amount.toDoubleOrNull()?.let { it > 0 } ?: false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .padding(16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = AccentPink, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                "Pay",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.weight(1f))

            // Next button - GRADIENT APPLIED
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        brush = if (isValid) mainGradientDiagonalBrush()
                        else androidx.compose.ui.graphics.Brush.linearGradient(listOf(TextMuted, TextMuted)),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .then(if (isValid) Modifier.clickable { onNext(amount, description) } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Next",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Amount display
        Text(
            "\u20BF",
            color = TextSecondary,
            fontSize = 20.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        BasicTextField(
            value = amount,
            onValueChange = { amount = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            singleLine = true,
            cursorBrush = SolidColor(AccentPink),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (amount.isEmpty()) {
                        Text("0", color = TextMuted, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    }
                    inner()
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "$0.00 USD",
            color = TextSecondary,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Description field
        BasicTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceMedium, RoundedCornerShape(12.dp))
                .padding(16.dp),
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            singleLine = true,
            cursorBrush = SolidColor(AccentPink),
            decorationBox = { inner ->
                if (description.isEmpty()) {
                    Text("Add a note", color = TextMuted, fontSize = 16.sp)
                }
                inner()
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Numeric keypad
        com.appxstudios.festivalconnection.ui.components.NumericKeypad(
            onDigit = { digit -> amount += digit },
            onBackspace = { if (amount.isNotEmpty()) amount = amount.dropLast(1) }
        )
    }
}
