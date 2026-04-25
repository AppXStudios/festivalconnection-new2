package com.appxstudios.festivalconnection.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Tracks BLE, Location, Notification, and Camera permission state.
// Each screen that needs to prompt the user registers its own ActivityResult
// launcher (via rememberLauncherForActivityResult in Compose) and calls
// refreshAll() afterward to update the StateFlows here.

class PermissionsManager private constructor(private val context: Context) {

    companion object {
        @Volatile private var instance: PermissionsManager? = null
        fun initialize(context: Context) { instance = PermissionsManager(context.applicationContext) }
        fun getInstance(): PermissionsManager = instance ?: throw IllegalStateException("Call initialize() first")
    }

    private val _bluetoothGranted = MutableStateFlow(false)
    val bluetoothGranted: StateFlow<Boolean> = _bluetoothGranted

    private val _locationGranted = MutableStateFlow(false)
    val locationGranted: StateFlow<Boolean> = _locationGranted

    private val _notificationGranted = MutableStateFlow(false)
    val notificationGranted: StateFlow<Boolean> = _notificationGranted

    private val _cameraGranted = MutableStateFlow(false)
    val cameraGranted: StateFlow<Boolean> = _cameraGranted

    val allRequiredGranted: Boolean
        get() = _bluetoothGranted.value && _locationGranted.value && _notificationGranted.value

    fun refreshAll() {
        _bluetoothGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }

        _locationGranted.value =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        _notificationGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        _cameraGranted.value =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
}
