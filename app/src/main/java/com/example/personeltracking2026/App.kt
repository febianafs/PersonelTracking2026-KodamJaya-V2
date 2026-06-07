package com.example.personeltracking2026

import android.app.Application
import android.util.Log
import com.example.personeltracking2026.core.mqtt.MqttManager
import com.example.personeltracking2026.ui.bluetooth.BluetoothLeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.personeltracking2026.core.device.DeviceMode
import org.maplibre.android.MapLibre

class App : Application() {

    lateinit var mqttManager: MqttManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Heart Rate global state ─────────────────────────────────────
    // Diupdate oleh PersonelViewModel/BluetoothViewModel saat data BLE masuk.
    // Dibaca oleh MqttLocationService saat build payload.
    @Volatile var currentHeartRate   : Int  = 0
    @Volatile var currentHeartRateTs : Long = 0L
    @Volatile var currentLat: Double = 0.0
    @Volatile var currentLon: Double = 0.0
    @Volatile var currentAccuracy: Float = 999f
    @Volatile var currentBodycamStream: Int = 0

    @Volatile
    var currentMode: DeviceMode = DeviceMode.NONE

    override fun onCreate() {
        super.onCreate()
        mqttManager = MqttManager(this)

        MapLibre.getInstance(this)

        // GLOBAL BLE COLLECTOR
        appScope.launch {
            BluetoothLeService.bpmValue.collectLatest { bpm ->

                currentHeartRate = bpm
                currentHeartRateTs = System.currentTimeMillis()

                Log.d("GLOBAL_HR", "HR = $bpm")
            }
        }

        // TAMBAH INI — init SosManager di level App
        // Sehingga SOS bisa diaktifkan dari Activity manapun (termasuk Settings)
        val session       = com.example.personeltracking2026.core.session.SessionManager(this)
        val deviceManager = com.example.personeltracking2026.utils.DeviceIdentityManager(this)
        val identity      = deviceManager.getIdentity()

        com.example.personeltracking2026.core.sos.SosManager.init(
            mqtt             = mqttManager,
            session          = session,
            serial           = identity.serial,
            id               = identity.androidId,
            type             = com.example.personeltracking2026.core.sos.SosManager.DeviceType.RADIO,
            locationProvider = { Triple(currentLat, currentLon, currentAccuracy) }
        )
    }
}
