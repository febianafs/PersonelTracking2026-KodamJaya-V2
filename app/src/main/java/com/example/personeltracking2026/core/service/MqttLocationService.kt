package com.example.personeltracking2026.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import com.example.personeltracking2026.data.model.LocationData
import com.example.personeltracking2026.utils.DeviceIdentityManager
import com.example.personeltracking2026.utils.StreamUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.personeltracking2026.core.device.DeviceMode

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
    private var bodycamPublishJob: Job? = null
    private var heartRatePublishJob: Job? = null
    private var lastPublishedHeartRate: Int? = null
    private var lastHeartRatePublishTime = 0L

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
                startBodycamIntervalPublisher()
                startHeartRateImmediatePublisher()
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
        bodycamPublishJob?.cancel()
        heartRatePublishJob?.cancel()
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
            val app = application as App
            app.updateGpsLocation(LocationData(lat, lon, accuracy, source, now))

            // THROTTLE: hanya publish kalau sudah lewat interval sejak publish terakhir
            if (now - lastPublishTime >= publishIntervalMs) {
                lastPublishTime = now
                Log.d(TAG, "Location [$source]: $lat, $lon → publishing")
                publishLocation()
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
        startBodycamIntervalPublisher()

        Log.d(TAG, "Location interval updated without restarting service")
    }

    // ─────────────────────────────────────────────
    //  Publish MQTT
    // ─────────────────────────────────────────────

    private fun publishLocation() {
        serviceScope.launch {
            val app = application as App
            val location = app.gpsState.value

            if (location == null) {
                Log.d(TAG, "No GPS state yet -> skip publish")
                return@launch
            }

            if (app.currentMode != DeviceMode.RADIO) {
                Log.d(TAG, "Not RADIO mode -> skip publish")
                return@launch
            }

            val mqttManager = app.mqttManager

            if (!mqttManager.isConnected()) {
                Log.d(TAG, "MQTT not connected, skip publish")
                return@launch
            }

            val deviceManager = DeviceIdentityManager(this@MqttLocationService)
            val identity = deviceManager.getIdentity()

            if (identity == null) {
                Log.e("MQTT", "Serial number is required")
                return@launch
            }

            val serialNumber = identity.serial
            val androidId    = identity.androidId

            val nowMs = System.currentTimeMillis()

            val heartRate = app.getHeartRateSnapshot(nowMs)
            Log.d(
                "HR_DEBUG",
                "SERVICE HR=${heartRate.bpm} ts=${heartRate.timestamp} expired=${heartRate.isExpired} now=$nowMs"
            )
            val payload = MqttPayloadBuilder.buildRadioDataPayload(
                session      = sessionManager,
                serialNumber = serialNumber,
                androidId    = androidId,
                lat          = location.lat,
                lon          = location.lon,
                acc          = location.accuracy,
                gpsTimestamp = location.timestamp,
                heartrate    = heartRate.bpm,
                heartrateTs  = heartRate.timestamp,
                batteryLevel = app.batteryState.value.percent,
                appVersion   = BuildConfig.APP_VERSION,
                rtmpUrl      = StreamUtils.getRtmpUrl(serialNumber)
            )

            mqttManager.publishRadioData(payload)
            Log.d("MQTT_SOURCE", "PUBLISH FROM SERVICE")
        }
    }

    private fun startBodycamIntervalPublisher() {
        bodycamPublishJob?.cancel()
        bodycamPublishJob = serviceScope.launch {
            while (true) {
                publishBodycamDataIfActive()
                delay(publishIntervalMs)
            }
        }
    }

    private fun startHeartRateImmediatePublisher() {
        heartRatePublishJob?.cancel()
        heartRatePublishJob = serviceScope.launch {
            val app = application as App
            app.effectiveHeartRate.collect { bpm ->
                val previousBpm = lastPublishedHeartRate
                lastPublishedHeartRate = bpm

                if (previousBpm == null || previousBpm == bpm) return@collect

                val now = System.currentTimeMillis()
                if (now - lastHeartRatePublishTime < 250L) return@collect

                lastHeartRatePublishTime = now
                Log.d(TAG, "Heart rate changed $previousBpm -> $bpm, immediate MQTT publish")
                publishLocation()
            }
        }
    }

    private fun publishBodycamDataIfActive() {
        val app = application as App

        if (app.currentMode != DeviceMode.BODYCAM) {
            Log.d(TAG, "Not BODYCAM mode -> skip bodycam publish")
            return
        }

        val mqttManager = app.mqttManager

        if (!mqttManager.isConnected()) {
            Log.d(TAG, "MQTT not connected, skip bodycam publish")
            return
        }

        val identity = DeviceIdentityManager(this).getIdentity()

        if (identity == null) {
            Log.e("MQTT", "Serial number is required")
            return
        }

        val serialNumber = identity.serial
        val payload = MqttPayloadBuilder.buildBodycamDataPayload(
            session = sessionManager,
            serialNumber = serialNumber,
            androidId = identity.androidId,
            streamUrl = StreamUtils.getRtmpUrl(serialNumber),
            stream = app.currentBodycamStream
        )

        Log.d(TAG, "BODYCAM publish interval=${publishIntervalMs}ms stream=${app.currentBodycamStream}")
        mqttManager.publishBodycamData(payload)
    }

    private fun parseIntervalToMs(interval: String): Long {
        return when {
            interval.contains("minute") -> {
                val m = interval.filter { it.isDigit() }.toLongOrNull() ?: 1L
                m * 60 * 1000
            }
            interval.contains("second") -> {
                val s = interval.filter { it.isDigit() }.toLongOrNull() ?: 5L
                s * 1000
            }
            else -> 5000L
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
                description = "Tracking Location Personel Active"
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
            .setContentTitle("Personel Tracking Active")
            .setContentText("Send Location Regulary")
            .setSmallIcon(R.drawable.ic_location_pin)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
