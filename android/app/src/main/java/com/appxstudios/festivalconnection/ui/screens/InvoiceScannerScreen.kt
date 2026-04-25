package com.appxstudios.festivalconnection.ui.screens

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.appxstudios.festivalconnection.services.WalletManager
import com.appxstudios.festivalconnection.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun InvoiceScannerScreen(
    onCancel: () -> Unit = {},
    onInvoiceParsed: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var pastedInvoice by remember { mutableStateOf("") }
    var showParsedAlert by remember { mutableStateOf(false) }
    var parsedAmountSat by remember { mutableStateOf<Long?>(null) }
    var parsedDescription by remember { mutableStateOf<String?>(null) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var paymentInProgress by remember { mutableStateOf(false) }
    var paymentResult by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Parsed alert dialog
    if (showParsedAlert) {
        AlertDialog(
            onDismissRequest = {
                if (!paymentInProgress) {
                    showParsedAlert = false
                    parseError = null
                    paymentResult = null
                }
            },
            title = { Text(
                if (paymentResult != null) "Payment Sent" else if (parseError != null) "Error" else "Invoice Parsed",
                color = Color.White
            ) },
            text = {
                Text(
                    when {
                        paymentResult != null -> paymentResult!!
                        parseError != null -> parseError!!
                        parsedAmountSat != null -> "Amount: ${parsedAmountSat} sats" +
                            if (!parsedDescription.isNullOrEmpty()) "\nDescription: $parsedDescription" else ""
                        else -> "Invoice ready to pay."
                    },
                    color = TextSecondary
                )
            },
            confirmButton = {
                if (paymentResult == null && parseError == null) {
                    TextButton(
                        onClick = {
                            paymentInProgress = true
                            coroutineScope.launch {
                                try {
                                    WalletManager.sendPayment(pastedInvoice, parsedAmountSat)
                                    paymentResult = "Payment successful!"
                                } catch (e: Exception) {
                                    paymentResult = "Payment failed: ${e.message}"
                                } finally {
                                    paymentInProgress = false
                                }
                            }
                        },
                        enabled = !paymentInProgress
                    ) {
                        Text(if (paymentInProgress) "Paying..." else "Pay", color = AccentPink)
                    }
                } else {
                    TextButton(onClick = {
                        showParsedAlert = false
                        parseError = null
                        if (paymentResult?.startsWith("Payment successful") == true) {
                            paymentResult = null
                            onInvoiceParsed(pastedInvoice)
                        }
                        paymentResult = null
                    }) {
                        Text("OK", color = AccentPink)
                    }
                }
            },
            dismissButton = {
                if (paymentResult == null && parseError == null) {
                    TextButton(onClick = {
                        showParsedAlert = false
                        parseError = null
                    }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            },
            containerColor = SurfaceDark
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
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

        // Tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TabItem(
                title = "Scanner",
                isSelected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.weight(1f)
            )
            TabItem(
                title = "Paste",
                isSelected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(20.dp))

        if (selectedTab == 0) {
            // Scanner mode with live camera
            if (cameraPermissionState.status.isGranted) {
                InvoiceCameraScanner(
                    onQRScanned = { code ->
                        pastedInvoice = code
                        // Run parse off the main thread — sdk.parse() is potentially blocking I/O.
                        coroutineScope.launch {
                            try {
                                val (amountSat, description) = WalletManager.parseInvoiceAsync(code)
                                parsedAmountSat = amountSat
                                parsedDescription = description
                                parseError = null
                            } catch (e: Exception) {
                                parseError = e.message ?: "Failed to parse invoice"
                                parsedAmountSat = null
                                parsedDescription = null
                            }
                            showParsedAlert = true
                        }
                    }
                )
            } else {
                // Camera permission not granted
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(horizontal = 24.dp)
                        .border(
                            width = 3.dp,
                            brush = mainGradientBrush(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GradientIcon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = "QR Scanner",
                            size = 64.dp
                        )
                        Text(
                            text = "Tap to enable camera",
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                        Button(
                            onClick = { cameraPermissionState.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(mainGradientBrush(), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Enable Camera", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Paste mode
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceMedium)
                        .padding(14.dp)
                ) {
                    if (pastedInvoice.isEmpty()) {
                        Text(
                            text = "Paste invoice here",
                            color = TextMuted,
                            fontSize = 15.sp
                        )
                    }
                    BasicTextField(
                        value = pastedInvoice,
                        onValueChange = { pastedInvoice = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(AccentPink),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = {
                        if (pastedInvoice.trim().isNotEmpty()) {
                            // Run parse off the main thread — sdk.parse() is potentially blocking I/O.
                            coroutineScope.launch {
                                try {
                                    val (amountSat, description) = WalletManager.parseInvoiceAsync(pastedInvoice.trim())
                                    parsedAmountSat = amountSat
                                    parsedDescription = description
                                    parseError = null
                                } catch (e: Exception) {
                                    parseError = e.message ?: "Failed to parse invoice"
                                    parsedAmountSat = null
                                    parsedDescription = null
                                }
                                showParsedAlert = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                brush = mainGradientBrush(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Continue",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun InvoiceCameraScanner(onQRScanned: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasScanned = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                if (!hasScanned.get()) {
                                    val result = decodeQRFromImageProxy(imageProxy)
                                    if (result != null && hasScanned.compareAndSet(false, true)) {
                                        ContextCompat.getMainExecutor(ctx).execute {
                                            onQRScanned(result)
                                        }
                                    }
                                }
                                imageProxy.close()
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (_: Exception) { }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
        )

        // Gradient border overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(3.dp, mainGradientBrush(), RoundedCornerShape(16.dp))
        )
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun decodeQRFromImageProxy(imageProxy: androidx.camera.core.ImageProxy): String? {
    val image = imageProxy.image ?: return null
    val yPlane = image.planes[0]
    val yBuffer = yPlane.buffer
    val yData = ByteArray(yBuffer.remaining())
    yBuffer.get(yData)

    val source = com.google.zxing.PlanarYUVLuminanceSource(
        yData,
        yPlane.rowStride,
        image.height,
        0, 0,
        image.width,
        image.height,
        false
    )

    val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
    return try {
        val reader = com.google.zxing.MultiFormatReader().apply {
            setHints(mapOf(
                com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE)
            ))
        }
        reader.decode(binaryBitmap).text
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun TabItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) SurfaceMedium else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.White else TextSecondary,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
