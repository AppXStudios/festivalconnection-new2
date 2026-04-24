package com.appxstudios.festivalconnection.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.appxstudios.festivalconnection.security.NostrIdentity
import com.appxstudios.festivalconnection.ui.theme.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.util.concurrent.Executors

@Composable
fun QRScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        Text(
            "QR Code",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 34.sp,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp)
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceDark,
            contentColor = Color.White,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("My QR", modifier = Modifier.padding(12.dp), color = if (selectedTab == 0) Color.White else TextMuted)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Scan", modifier = Modifier.padding(12.dp), color = if (selectedTab == 1) Color.White else TextMuted)
            }
        }

        if (selectedTab == 0) {
            MyQRTab()
        } else {
            ScanTab()
        }
    }
}

@Composable
private fun MyQRTab() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE)
    val defaultId = prefs.getString("fc_handle", "") ?: ""
    // Use the real Nostr public key if available, fall back to stored handle
    val publicKey = if (NostrIdentity.isInitialized && NostrIdentity.publicKeyHex.isNotEmpty()) {
        NostrIdentity.publicKeyHex
    } else {
        defaultId
    }
    val nickname = prefs.getString("fc_nickname", if (publicKey.isNotEmpty()) "Peer ${publicKey.take(4).uppercase()}" else "") ?: ""
    val rawHandle = publicKey
    val handle = if (rawHandle.isNotEmpty()) "@${rawHandle.take(16)}" else ""
    val handleForUrl = rawHandle

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val qrBitmap = remember(handleForUrl) {
            generateQRCode("festivalconnection://peer/$handleForUrl", 550)
        }
        qrBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = Modifier
                    .size(220.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(nickname, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(handle, fontSize = 15.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Text("Scan to connect", fontSize = 13.sp, color = TextMuted)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ScanTab() {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var scannedPeerKey by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    if (cameraPermissionState.status.isGranted) {
        if (scannedPeerKey != null) {
            ScannedPeerView(
                peerKey = scannedPeerKey!!,
                context = context,
                onScanAgain = { scannedPeerKey = null }
            )
        } else {
            CameraQRScanner(
                onQRScanned = { code ->
                    if (code.startsWith("festivalconnection://peer/")) {
                        val peerKey = code.removePrefix("festivalconnection://peer/")
                        if (peerKey.isNotEmpty()) {
                            scannedPeerKey = peerKey
                            // Save the peer connection and handle
                            val prefs = context.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE)
                            val existingPeers = prefs.getStringSet("connected_peers", mutableSetOf()) ?: mutableSetOf()
                            val updatedPeers = existingPeers.toMutableSet()
                            updatedPeers.add(peerKey)
                            val peerHandle = peerKey.take(8).lowercase()
                            prefs.edit()
                                .putStringSet("connected_peers", updatedPeers)
                                .putString("peer_handle_$peerKey", peerHandle)
                                .apply()

                            // Announce is handled by PacketProcessor via the mesh layer
                        }
                    }
                }
            )
        }
    } else {
        // Permission not granted
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GradientIcon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = "Camera",
                size = 64.dp
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Camera Access Needed",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Allow camera access to scan\nFestival Connection QR codes",
                fontSize = 15.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            if (cameraPermissionState.status.shouldShowRationale) {
                // Permission was denied, need to go to settings
                GradientButton(
                    text = "Open Settings",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            } else {
                GradientButton(
                    text = "Enable Camera",
                    onClick = { cameraPermissionState.launchPermissionRequest() }
                )
            }
        }
    }
}

@Composable
private fun CameraQRScanner(onQRScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasScanned = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        Box(contentAlignment = Alignment.Center) {
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
                                        val result = decodeQRFromImage(imageProxy)
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
                    .size(280.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            // Gradient border overlay
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .border(3.dp, mainGradientBrush(), RoundedCornerShape(20.dp))
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Point at a QR code to connect",
            fontSize = 15.sp,
            color = TextSecondary
        )

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ScannedPeerView(
    peerKey: String,
    context: Context,
    onScanAgain: () -> Unit
) {
    val prefs = context.getSharedPreferences("fc_prefs", Context.MODE_PRIVATE)
    val peerName = "Peer ${peerKey.take(4).uppercase()}"

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        GradientIcon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Connected",
            size = 64.dp
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "Peer Connected!",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = Color.White
        )

        Spacer(Modifier.height(8.dp))
        Text(
            peerName,
            fontSize = 15.sp,
            color = TextSecondary
        )

        Spacer(Modifier.height(24.dp))
        GradientButton(
            text = "Scan Another",
            onClick = onScanAgain
        )

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun GradientButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.padding(horizontal = 40.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(mainGradientBrush(), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color.White
            )
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun decodeQRFromImage(imageProxy: ImageProxy): String? {
    val image = imageProxy.image ?: return null
    val yPlane = image.planes[0]
    val yBuffer = yPlane.buffer
    val yData = ByteArray(yBuffer.remaining())
    yBuffer.get(yData)

    val source = PlanarYUVLuminanceSource(
        yData,
        yPlane.rowStride,
        image.height,
        0, 0,
        image.width,
        image.height,
        false
    )

    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
    return try {
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }
        reader.decode(binaryBitmap).text
    } catch (_: Exception) {
        null
    }
}

private fun generateQRCode(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
