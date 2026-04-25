package com.appxstudios.festivalconnection.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appxstudios.festivalconnection.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay

@Composable
fun InvoiceDisplayScreen(
    invoice: String = "",
    amountUSD: Double = 0.0,
    description: String = "",
    onDone: () -> Unit = {},
    onShare: (String) -> Unit = {}
) {
    var secondsRemaining by remember { mutableIntStateOf(600) }
    val clipboardManager = LocalClipboardManager.current

    // Countdown timer
    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
        }
    }

    val timeString = remember(secondsRemaining) {
        val m = secondsRemaining / 60
        val s = secondsRemaining % 60
        String.format(java.util.Locale.US, "%d:%02d", m, s)
    }

    // Generate QR code bitmap
    val qrBitmap = remember(invoice) {
        generateQrBitmap(
            content = invoice.ifEmpty { "festivalconnection://payment" },
            size = 660
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack),
        horizontalAlignment = Alignment.CenterHorizontally
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

        Spacer(Modifier.height(12.dp))

        // Title
        Text(
            "Share Payment Request",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            color = Color.White
        )

        Spacer(Modifier.height(24.dp))

        // QR Code
        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "QR Code",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(8.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        // Amount
        Text(
            text = String.format(java.util.Locale.US, "$%.2f", amountUSD),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )

        // Description
        if (description.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 15.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        // Countdown timer
        Text(
            text = "Expires in $timeString",
            color = TextMuted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(24.dp))

        // Copy + Share buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                clipboardManager.setText(AnnotatedString(invoice))
            }) {
                Text(
                    text = "Copy",
                    color = AccentPink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            TextButton(onClick = { onShare(invoice) }) {
                Text(
                    text = "Share",
                    color = AccentPink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix.get(x, y)) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
