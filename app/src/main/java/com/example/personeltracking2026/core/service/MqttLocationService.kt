package com.example.personeltracking2026.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.personeltracking2026.App
import com.example.personeltracking2026.BuildConfig
import com.example.personeltracking2026.R
import com.example.personeltracking2026.core.location.AppLocationManager
import com.example.personeltracking2026.core.mqtt.MqttPayloadBuilder
import com.example.personeltracking2026.core.mqtt.MqttReconnectManager
import com.example.personeltracking2026.core.session.SessionManager
import com.example.personeltracking2026.core.utils.Constants
import com.example.personeltracking2026.utils.DeviceIdentityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MqttLocationService : Service() {

    companion object {
        private const val TAG           = "MqttLocationService"
        const val CHANNEL_ID            = "mqtt_location_channel"
        const val NOTIFICATION_ID       = 1001
        private const val ACTION_START  = "ACTION_START"
        private const val ACTION_STOP   = "ACTION_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, MqttLocationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MqttLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var sessionManager: SessionManager
    private lateinit var appLocationManager: AppLocationManager
    private lateinit var reconnectManager: MqttReconnectManager
    private lateinit var wakeLock: PowerManager.WakeLock

    // Throttle — cegah publish duplikat
    private var lastPublishTime = 0L
    private var publishIntervalMs = 5000L

    private val intervalChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Constants.ACTION_INTERVAL_CHANGED) return

            val intervalText = intent.getStringExtra(Constants.EXTRA_INTERVAL_TEXT)
                ?: Constants.DEFAULT_INTERVAL_TEXT

            Log.d(TAG, "Interval changed broadcast received: $intervalText")
            applyNewInterval(intervalText)
        }
    }

    // ─────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        sessionManager      = SessionManager(this)
        appLocationManager  = AppLocationManager(this)
        reconnectManager    = MqttReconnectManager(this, (application as App).mqttManager)

        // WakeLock agar CPU tidak sleep saat publish
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PersonelTracking::MqttWakeLock"
        ).apply { acquire(60 * 60 * 1000L) } // max 1 jam, auto release

        createNotificationChannel()
        val filter = IntentFilter(Constants.ACTION_INTERVAL_CHANGED)

        ContextCompat.registerReceiver(
            this,
            intervalChangedReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "Service starting")

                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        buildNotification(),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                        } else {
                            0
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "startForeground failed: ${e.message}")
                    stopSelf()
                    return START_NOT_STICKY
                }

                reconnectManager.start()
                startLocationUpdates()
            }
            ACTION_STOP -> {
                Log.d(TAG, "Service stopping")
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        reconnectManager.stop()
        appLocationManager.stopUpdates()
        serviceScope.cancel()
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        try {
            unregisterReceiver(intervalChangedReceiver)
        } catch (_: Exception) {
        }
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────────────────────
    //  Location
    // ─────────────────────────────────────────────

    private fun startLocationUpdates() {
        val prefs = getSharedPreferences("mqtt_settings", Context.MODE_PRIVATE)
        val intervalStr = prefs.getString("interval", Constants.DEFAULT_INTERVAL_TEXT)
            ?: Constants.DEFAULT_INTERVAL_TEXT
        publishIntervalMs = parseIntervalToMs(intervalStr)

        appLocationManager.setInterval(publishIntervalMs)

        // reset throttle saat mulai
        lastPublishTime = 0L

        appLocationManager.onLocationUpdate = { lat, lon, accuracy, source ->
            val now = System.currentTimeMillis()

            // THROTTLE: hanya publish kalau sudah lewat interval sejak publish terakhir
            if (now - lastPublishTime >= publishIntervalMs) {
                lastPublishTime = now
                Log.d(TAG, "Location [$source]: $lat, $lon → publishing")
                publishLocation(lat, lon, accuracy)
            } else {
                Log.d(TAG, "Location [$source]: throttled, skip")
            }
        }

        appLocationManager.onLocationError = { message ->
            Log.e(TAG, "Location error: $message")
        }

        appLocationManager.startUpdates()
        Log.d(TAG, "Location updates started — interval: ${publishIntervalMs / 1000}s (${publishIntervalMs} ms)")
    }

    private fun applyNewInterval(intervalText: String) {
        val newIntervalMs = parseIntervalToMs(intervalText)

        if (newIntervalMs == publishIntervalMs) {
            Log.d(TAG, "Interval unchanged, skip restart: $newIntervalMs ms")
            return
        }

        Log.d(TAG, "Applying new interval: $intervalText -> $newIntervalMs ms")

        publishIntervalMs = newIntervalMs
        lastPublishTime = 0L

        appLocationManager.stopUpdates()
        appLocationManager.setInterval(publishIntervalMs)
        appLocationManager.startUpdates()

        Log.d(TAG, "Location interval updated without restarting service")
    }

    // ─────────────────────────────────────────────
    //  Publish MQTT
    // ─────────────────────────────────────────────

    private fun publishLocation(lat: Double, lon: Double, accuracy: Float) {
        serviceScope.launch {
            val mqttManager = (application as App).mqttManager
            if (!mqttManager.isConnected()) {
                Log.d(TAG, "MQTT not connected, skip publish")
                return@launch
            }

            val deviceManager = DeviceIdentityManager(this@MqttLocationService)
            val identity = deviceManager.getIdentity()

            if (identity == null) {
                Log.e("MQTT", "Serial belum di-set")
                return@launch
            }

            val serialNumber = identity.serial
            val androidId    = identity.androidId

            val nowMs = System.currentTimeMillis()

            // Baca HR dari App-level state (diisi oleh PersonelViewModel via BLE)
            val app          = application as? com.example.personeltracking2026.App
            val hr           = app?.currentHeartRate   ?: 0
            val hrTs         = app?.currentHeartRateTs?.takeIf { it > 0 } ?: nowMs
            val payload = MqttPayloadBuilder.buildRadioDataPayload(
                session      = sessionManager,
                serialNumber = serialNumber,
                androidId    = androidId,
                lat          = lat,
                lon          = lon,
                acc          = accuracy,
                gpsTimestamp = nowMs,
                heartrate    = hr,
                heartrateTs  = hrTs,
                batteryLevel = getBatteryLevel(),
                appVersion   = BuildConfig.APP_VERSION,
                rtmpUrl      = StreamUtils.getRtmpUrl(serialNumber)
            )

            mqttManager.publishRadioData(payload)
        }
    }

    private fun getBatteryLevel(): Int {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) { 0 }
    }

    private fun parseIntervalToMs(interval: String): Long {
        return when {
            interval.contains("minute") -> {
                val m = interval.filter { it.isDigit() }.toLongOrNull() ?: 1L
                m * 60 * 1000
            }
            interval.contains("second") -> {
                val s = interval.filter { it.isDigit() }.toLongOrNull() ?: 10L
                s * 1000
            }
            else -> 10000L
        }
    }

    // ─────────────────────────────────────────────
    //  Notification
    // ─────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Personel Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracking lokasi personel aktif"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Personel Tracking Aktif")
            .setContentText("Mengirim lokasi secara berkala")
            .setSmallIcon(R.drawable.ic_location_pin)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}