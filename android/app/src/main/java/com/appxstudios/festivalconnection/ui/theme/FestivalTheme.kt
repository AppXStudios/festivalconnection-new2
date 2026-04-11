package com.appxstudios.festivalconnection.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Gradient stops (4-stop purple-fuchsia-pink-tangerine — matches iOS exactly)
val GradientPurple = Color(0xFF7B2FBE)
val GradientFuchsia = Color(0xFFC026D3)
val GradientPink = Color(0xFFEC4899)
val GradientTangerine = Color(0xFFF59E0B)

// Backward-compat aliases
val GradientMagenta = GradientFuchsia
val GradientCoral = GradientPink
val GradientOrange = GradientTangerine
val GradientAmber = GradientTangerine

// Accent
val AccentPink = GradientPink

// Backgrounds & Surfaces
val BackgroundBlack = Color(0xFF000000)
val SurfaceDark = Color(0xFF1C1C1E)
val SurfaceMedium = Color(0xFF2C2C2E)

// Text
val TextPrimary = Color.White
val TextSecondary = Color(0xFF999999)
val TextMuted = Color(0xFF666666)

// Status
val PresenceGreen = Color(0xFF32D74B)
val ErrorRed = Color.Red

// Payment
val PaymentBorder = Color(0xFF7A3A00)

// Icon Colors (kept for backward compat)
val IconOrange = GradientOrange
val IconBlue = Color(0xFF409CFF)
val IconPurple = GradientPurple
val IconGreen = Color(0xFF32D74B)
val IconRed = Color(0xFFFF453A)
val IconGold = Color(0xFFFFCC00)
val IconGray = Color(0xFF8C8C8C)

val MainGradientColors = listOf(GradientPurple, GradientFuchsia, GradientPink, GradientTangerine)

fun mainGradientBrush(): Brush = Brush.linearGradient(colors = MainGradientColors)

fun mainGradientVerticalBrush(): Brush = Brush.verticalGradient(colors = MainGradientColors)

fun mainGradientDiagonalBrush(): Brush = Brush.linearGradient(
    colors = MainGradientColors,
    start = Offset(0f, 0f),
    end = Offset(1000f, 1000f)
)

private val FestivalColorScheme = darkColorScheme(
    background = BackgroundBlack,
    surface = SurfaceDark,
    primary = AccentPink,
    onPrimary = Color.White,
    secondary = GradientPurple,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun FestivalConnectionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FestivalColorScheme,
        content = content
    )
}

@Composable
fun GradientText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        letterSpacing = letterSpacing,
        style = TextStyle(
            brush = mainGradientBrush()
        )
    )
}

@Composable
fun GradientIcon(
    imageVector: ImageVector,
    contentDescription: String? = null,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .graphicsLayer(alpha = 0.99f)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.linearGradient(MainGradientColors),
                    blendMode = BlendMode.SrcAtop
                )
            },
        tint = Color.White
    )
}
