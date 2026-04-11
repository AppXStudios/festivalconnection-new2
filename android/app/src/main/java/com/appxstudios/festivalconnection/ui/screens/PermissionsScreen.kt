package com.appxstudios.festivalconnection.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appxstudios.festivalconnection.ui.theme.*

@Composable
fun PermissionsScreen(
    onGetStarted: () -> Unit
) {
    var bluetoothGranted by remember { mutableStateOf(false) }
    var locationGranted by remember { mutableStateOf(false) }
    var wifiGranted by remember { mutableStateOf(false) }
    var notificationsGranted by remember { mutableStateOf(false) }

    val allGranted = bluetoothGranted && locationGranted && wifiGranted

    val permissionsToRequest = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        bluetoothGranted = results.entries.any { it.key.contains("BLUETOOTH") && it.value }
        locationGranted = results.entries.any { it.key.contains("LOCATION") && it.value }
        wifiGranted = results.entries.any { (it.key.contains("WIFI") || it.key.contains("LOCATION")) && it.value }
        notificationsGranted = results.entries.any { it.key.contains("NOTIFICATION") && it.value }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissionsToRequest.toTypedArray())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Title - GRADIENT TEXT APPLIED
        GradientText(
            "Permissions Required",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Festival Connection needs these permissions to enable CrowdSync\u2122",
            color = TextSecondary,
            fontSize = 15.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Permission rows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bluetooth - GRADIENT ICON APPLIED
            PermissionRow(
                icon = Icons.Filled.CellTower,
                name = "Bluetooth",
                description = "Used by CrowdSync\u2122 to discover\nnearby people",
                isGranted = bluetoothGranted
            )

            // Wi-Fi - GRADIENT ICON APPLIED
            PermissionRow(
                icon = Icons.Filled.Wifi,
                name = "Wi-Fi",
                description = "Used by CrowdSync\u2122 for nearby\nconnections",
                isGranted = wifiGranted
            )

            // Location - GRADIENT ICON APPLIED
            PermissionRow(
                icon = Icons.Filled.LocationOn,
                name = "Location",
                description = "Required to find people nearby\nand enable local channels",
                isGranted = locationGranted
            )

            // Notifications - GRADIENT ICON APPLIED
            PermissionRow(
                icon = Icons.Filled.Notifications,
                name = "Notifications",
                description = "Optional \u2014 so you never miss\na message",
                isGranted = notificationsGranted
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Get Started / Enable All button - GRADIENT APPLIED
        Button(
            onClick = {
                if (allGranted) {
                    onGetStarted()
                } else {
                    launcher.launch(permissionsToRequest.toTypedArray())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    brush = mainGradientDiagonalBrush(),
                    shape = RoundedCornerShape(16.dp)
                ),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Text(
                if (allGranted) "Get Started" else "Enable All",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    name: String,
    description: String,
    isGranted: Boolean,
    tint: Color = AccentPink
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        // GRADIENT ICON APPLIED
        GradientIcon(
            imageVector = icon,
            size = 28.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }

        if (isGranted) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Granted",
                tint = PresenceGreen,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
