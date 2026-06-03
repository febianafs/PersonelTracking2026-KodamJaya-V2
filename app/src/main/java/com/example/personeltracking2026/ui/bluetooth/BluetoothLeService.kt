package com.example.personeltracking2026.ui.bluetooth

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.personeltracking2026.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BluetoothLeService : Service() {

    companion object {
        private const val TAG = "BLEService"
        private const val NOTIF_CHANNEL_ID = "ble_channel"
        private const val NOTIF_ID = 101

        val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        val bpmValue        = MutableStateFlow(0)
        val connectedDevice = MutableStateFlow<BluetoothDeviceModel?>(null)

        // UUID Heart Rate
        val HEART_RATE_SERVICE  = java.util.UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_CHAR     = java.util.UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CLIENT_CHAR_CONFIG  = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

    // Binder untuk Activity bisa panggil fungsi langsung
    inner class LocalBinder : Binder() {
        fun getService() = this@BluetoothLeService
    }
    private val binder = LocalBinder()

    private var bluetoothGatt: BluetoothGatt? = null
    private var currentDeviceAddress: String? = null

    // ══════════════════════════════════════════════════════════════════════
    // LIFECYCLE SERVICE
    // ══════════════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Bluetooth standby"))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        disconnectGatt()
    }

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC API — dipanggil dari Activity
    // ══════════════════════════════════════════════════════════════════════

    fun connect(device: BluetoothDeviceModel, adapter: android.bluetooth.BluetoothAdapter) {
        currentDeviceAddress = device.address

        // Paksa disconnect gatt sebelumnya sebelum connect device
        if (bluetoothGatt != null) {
            disconnectGatt()
        }

//        if (connectionState.value == ConnectionState.CONNECTED) {
//            Log.d(TAG, "Already connected, skip")
//            return
//        }

        connectionState.value = ConnectionState.CONNECTING
        connectedDevice.value = device.copy(state = DeviceState.CONNECTING)

        val rawDevice = adapter.getRemoteDevice(device.address) ?: run {
            connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        try {
            bluetoothGatt = rawDevice.connectGatt(this, false, gattCallback)
            updateNotification("Menghubungkan ke ${device.name}...")
        } catch (e: SecurityException) {
            Log.e(TAG, "connectGatt failed: ${e.message}")
            connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun disconnect() {
        disconnectGatt()
        bluetoothGatt = null
        currentDeviceAddress = null
        connectionState.value = ConnectionState.DISCONNECTED
        bpmValue.value = 0
        connectedDevice.value = null
        updateNotification("Bluetooth standby")
    }

    fun isConnected() = connectionState.value == ConnectionState.CONNECTED

    // ══════════════════════════════════════════════════════════════════════
    // GATT CALLBACK
    // ══════════════════════════════════════════════════════════════════════

    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT Connected")
                    try { gatt.discoverServices() } catch (e: SecurityException) { }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt.close()
                    bluetoothGatt = null
                    Log.d(TAG, "GATT Disconnected")
                    connectionState.value = ConnectionState.DISCONNECTED
                    bpmValue.value = 0
                    connectedDevice.value = null
                    updateNotification("Bluetooth standby")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

            connectionState.value = ConnectionState.CONNECTED
            connectedDevice.value = connectedDevice.value?.copy(state = DeviceState.CONNECTED)
            updateNotification("Terhubung ke ${connectedDevice.value?.name ?: "device"}")

            // Subscribe heart rate notification
            val service = gatt.getService(HEART_RATE_SERVICE) ?: return
            val char    = service.getCharacteristic(HEART_RATE_CHAR) ?: return

            try {
                gatt.setCharacteristicNotification(char, true)
                val descriptor = char.getDescriptor(CLIENT_CHAR_CONFIG)
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (descriptor != null) gatt.writeDescriptor(descriptor)
            } catch (e: SecurityException) {
                Log.e(TAG, "setNotification failed: ${e.message}")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (gatt.device.address != currentDeviceAddress) {
                Log.d("BLE", "IGNORE OLD DEVICE: ${gatt.device.address}")
                return
            }

            if (characteristic.uuid == HEART_RATE_CHAR) {
                val flag = characteristic.properties
                val bpm = if (flag and 0x01 != 0) {
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1) ?: 0
                } else {
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1) ?: 0
                }
                bpmValue.value = bpm
                Log.d(TAG, "BPM: $bpm")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // INTERNAL
    // ══════════════════════════════════════════════════════════════════════

    private fun disconnectGatt() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (e: SecurityException) {
            Log.e(TAG, "disconnect failed: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // NOTIFICATION (Foreground Service wajib punya ini)
    // ══════════════════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Bluetooth Connection",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("Personel Tracking")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIF_ID, buildNotification(text))
    }
}