package com.appxstudios.festivalconnection.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.appxstudios.festivalconnection.security.NostrIdentity
import com.appxstudios.festivalconnection.security.IdentityManager
import com.appxstudios.festivalconnection.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SettingUpScreen(onInitialized: () -> Unit) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    LaunchedEffect(Unit) {
        // Initialize real identity alongside the UX delay
        NostrIdentity.initialize(context)
        IdentityManager.initialize(context)

        // Store public key and derived handle/nickname in shared prefs
        val prefs = context.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE)
        val fingerprint = NostrIdentity.publicKeyHex
        if (prefs.getString("fc_handle", "").isNullOrEmpty()) {
            prefs.edit()
                .putString("fc_nickname", "Peer ${fingerprint.take(4).uppercase()}")
                .putString("fc_handle", fingerprint.take(8))
                .putString("fc_public_key", fingerprint.take(64))
                .apply()
        }

        delay(2000)
        onInitialized()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GradientText(
                text = "Festival Connection",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Setting up your connection...",
                fontSize = 15.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(32.dp))

            Box(modifier = Modifier.size(80.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Track
                    drawCircle(
                        color = SurfaceDark,
                        radius = size.minDimension / 2,
                        style = Stroke(width = 4.dp.toPx())
                    )
                    // Gradient arc
                    drawArc(
                        brush = mainGradientBrush(),
                        startAngle = rotation,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height)
                    )
                }
            }
        }
    }
}
