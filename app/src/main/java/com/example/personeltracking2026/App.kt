package com.example.personeltracking2026

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.example.personeltracking2026.core.mqtt.MqttManager
import com.example.personeltracking2026.data.model.LocationData
import com.example.personeltracking2026.ui.bluetooth.BluetoothLeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.personeltracking2026.core.device.DeviceMode
import org.maplibre.android.MapLibre

data class HeartRateSnapshot(
    val bpm: Int,
    val timestamp: Long,
    val isExpired: Boolean
)

data class BatterySnapshot(
    val percent: Int = 0,
    val isCharging: Boolean = false,
    val timestamp: Long = 0L
)

class App : Application() {

    lateinit var mqttManager: MqttManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _effectiveHeartRate = MutableStateFlow(0)
    val effectiveHeartRate: StateFlow<Int> = _effectiveHeartRate.asStateFlow()
    private val _batteryState = MutableStateFlow(BatterySnapshot())
    val batteryState: StateFlow<BatterySnapshot> = _batteryState.asStateFlow()
    private val _gpsState = MutableStateFlow<LocationData?>(null)
    val gpsState: StateFlow<LocationData?> = _gpsState.asStateFlow()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryFromIntent(intent)
        }
    }

    companion object {
        const val HEART_RATE_STALE_TIMEOUT_MS = 5_000L
    }

    // ── Heart Rate global state ─────────────────────────────────────
    // Diupdate oleh PersonelViewModel/BluetoothViewModel saat data BLE masuk.
    // Dibaca oleh MqttLocationService saat build payload.
    @Volatile
    var currentHeartRate: Int = 0
        private set

    @Volatile
    var currentHeartRateTs: Long = 0L
        private set
    @Volatile var currentLat: Double = 0.0
    @Volatile var currentLon: Double = 0.0
    @Volatile var currentAccuracy: Float = 999f
    @Volatile var currentBodycamStream: Int = 0

    @Volatile
    var currentMode: DeviceMode = DeviceMode.NONE

    fun getHeartRateSnapshot(nowMs: Long = System.currentTimeMillis()): HeartRateSnapshot {
        val bpm = currentHeartRate
        val timestamp = currentHeartRateTs
        val isExpired = timestamp <= 0L || nowMs - timestamp > HEART_RATE_STALE_TIMEOUT_MS

        return HeartRateSnapshot(
            bpm = if (isExpired) 0 else bpm,
            timestamp = timestamp.takeIf { it > 0L } ?: nowMs,
            isExpired = isExpired
        )
    }

    private fun refreshEffectiveHeartRate(nowMs: Long = System.currentTimeMillis()) {
        val bpm = getHeartRateSnapshot(nowMs).bpm
        if (_effectiveHeartRate.value != bpm) {
            _effectiveHeartRate.value = bpm
        }
    }

    fun updateHeartRateFromBle(
        bpm: Int,
        nowMs: Long = System.currentTimeMillis(),
        source: String = "BLE"
    ) {
        val normalizedBpm = bpm.coerceAtLeast(0)
        val duplicateWindowMs = nowMs - currentHeartRateTs
        val isDuplicate = normalizedBpm == currentHeartRate && duplicateWindowMs in 0L..250L

        if (!isDuplicate) {
            currentHeartRate = normalizedBpm
            currentHeartRateTs = nowMs
        }

        refreshEffectiveHeartRate(nowMs)
        Log.d("GLOBAL_HR", "HR=$normalizedBpm source=$source ts=${currentHeartRateTs}")
    }

    fun refreshBatteryFromSystem() {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            updateBatteryFromIntent(intent)
            return
        }

        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        updateBattery(
            percent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            isCharging = bm.isCharging
        )
    }

    private fun updateBatteryFromIntent(intent: Intent?) {
        if (intent == null) return

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt()
        } else {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        updateBattery(percent, isCharging)
    }

    private fun updateBattery(percent: Int, isCharging: Boolean) {
        if (percent < 0) return

        val next = BatterySnapshot(
            percent = percent.coerceIn(0, 100),
            isCharging = isCharging,
            timestamp = System.currentTimeMillis()
        )

        if (_batteryState.value != next) {
            _batteryState.value = next
        }
    }

    fun updateGpsLocation(location: LocationData) {
        _gpsState.value = location
        currentLat = location.lat
        currentLon = location.lon
        currentAccuracy = location.accuracy
    }

    override fun onCreate() {
        super.onCreate()
        mqttManager = MqttManager(this)

        MapLibre.getInstance(this)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        refreshBatteryFromSystem()

        // GLOBAL BLE COLLECTOR
        appScope.launch {
            BluetoothLeService.bpmReading.collectLatest { reading ->
                updateHeartRateFromBle(
                    bpm = reading.bpm,
                    nowMs = reading.timestamp,
                    source = "APP_COLLECTOR"
                )
            }
        }

        // TAMBAH INI — init SosManager di level App
        // Sehingga SOS bisa diaktifkan dari Activity manapun (termasuk Settings)
        appScope.launch {
            while (true) {
                refreshEffectiveHeartRate()
                delay(250)
            }
        }

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
