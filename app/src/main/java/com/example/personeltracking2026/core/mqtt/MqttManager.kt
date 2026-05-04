package com.example.personeltracking2026.core.mqtt

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.personeltracking2026.core.mqtt.queue.MqttQueueManager
import com.example.personeltracking2026.core.utils.MqttLogger
import com.example.personeltracking2026.data.model.BodycamDataPayload
import com.example.personeltracking2026.data.model.BodycamSosPayload
import com.example.personeltracking2026.data.model.RadioDataPayload
import com.example.personeltracking2026.data.model.RadioSosPayload
import com.google.gson.Gson
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class MqttManager(private val context: Context) {

    lateinit var queueManager: MqttQueueManager

    init {
        queueManager = MqttQueueManager(context)
    }

    companion object {
        private const val TAG            = "MqttManager"
        const val TOPIC_RADIO_DATA       = "tjw/radio/data"
        const val TOPIC_BODYCAM_DATA     = "tjw/bodycam/data"
        const val TOPIC_RADIO_SOS        = "tjw/radio/sos"
        const val TOPIC_BODYCAM_SOS      = "tjw/bodycam/sos"
        const val QOS_DATA               = 1
        const val QOS_SOS                = 2
        private const val MAX_RETRY      = 5
        private const val RETRY_DELAY_MS = 5_000L
    }

    var onConnected      : (() -> Unit)?                                = null
    var onDisconnected   : (() -> Unit)?                                = null
    var onPublishSuccess : ((topic: String) -> Unit)?                   = null
    var onPublishFailed  : ((topic: String, reason: String) -> Unit)?   = null

    //private val gson  = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var client             : Mqtt3AsyncClient? = null
    private var retryJob           : Job?              = null
    private var retryCount                             = 0
    private var isIntentionallyStopped                 = false

    private var reconnectAttempts = 0

    // ─── PUBLIC ──────────────────────────────────────────────────────────────

    fun connect() {
        isIntentionallyStopped = false
        retryCount = 0
        scope.launch { doConnect() }
    }

    fun disconnect() {
        isIntentionallyStopped = true
        retryJob?.cancel()
        scope.launch {
            try {
                client?.disconnect()
                MqttLogger.connect(null, false)
            } catch (e: Exception) {
                MqttLogger.error("Disconnect error: ${e.message}")
            }
        }
    }

    /**
     * Dipanggil setelah user Save settings baru di SettingsActivity.
     * Disconnect dari broker lama, reconnect dengan config terbaru.
     */
    fun reconnectWithNewSettings() {
        isIntentionallyStopped = true
        retryJob?.cancel()
        scope.launch {
            try { client?.disconnect() } catch (e: Exception) { /* ignore */ }
            delay(500)
            isIntentionallyStopped = false
            retryCount = 0
            doConnect()
        }
    }

    fun publishDirect(topic: String, payload: String) {

        client?.publishWith()
            ?.topic(topic)
            ?.payload(payload.toByteArray())
            ?.send()

        MqttLogger.publish(topic, payload)
    }

    fun isConnected(): Boolean = client?.state?.isConnected == true

    fun publishRadioData(payload: RadioDataPayload) {
        scope.launch {
            val json = buildRadioDataJson(payload)
            publish(TOPIC_RADIO_DATA, json, QOS_DATA)
        }
    }

    fun publishRadioSos(payload: RadioSosPayload) {
        scope.launch {
            val json = buildRadioSosJson(payload)
            publish(TOPIC_RADIO_SOS, json, QOS_SOS)
        }
    }

    fun publishBodycamData(payload: BodycamDataPayload) {
        scope.launch {
            val json = buildBodycamDataJson(payload)
            publish(TOPIC_BODYCAM_DATA, json, QOS_DATA)
        }
    }
    fun publishBodycamSos(payload: BodycamSosPayload) {
        scope.launch {
            val json = buildBodycamSosJson(payload)
            publish(TOPIC_BODYCAM_SOS, json, QOS_SOS)
        }
    }

//    fun publishData(payload: RadioDataPayload) {
//        scope.launch { publish(TOPIC_DATA, gson.toJson(payload), QOS_DATA) }
//    }
//
//    fun publishSos(payload: RadioSosPayload) {
//        scope.launch { publish(TOPIC_SOS, gson.toJson(payload), QOS_SOS) }
//    }

    // ─── INTERNAL ────────────────────────────────────────────────────────────

    private fun doConnect() {
        // Selalu baca config terbaru dari MqttConfigManager — bukan dari prefs langsung
        val config = MqttConfigManager(context).load()
        val host   = config.host
        val port   = if (config.useWebSocket) config.wsPort else config.tcpPort
        val user   = config.username
        val pass   = config.password

        val clientId = "android_${Build.MODEL}_${System.currentTimeMillis()}"
            .replace(" ", "_")
            .take(23)

        val builder = Mqtt3Client.builder()
            .identifier(clientId)
            .serverHost(host)
            .serverPort(port)

        // WebSocket hanya kalau dipakai
        if (config.useWebSocket) {
            builder.webSocketConfig()
                .serverPath("/")
                .applyWebSocketConfig()
        }

        // Authentication
        builder.simpleAuth()
            .username(user)
            .password(pass.toByteArray(StandardCharsets.UTF_8))
            .applySimpleAuth()

        // Build client tanpa disconnectedListener
        // Reconnect dihandle oleh MqttReconnectManager
        client = builder.buildAsync()

        client?.connect()
            ?.whenComplete { connAck: Mqtt3ConnAck?, throwable: Throwable? ->
                if (throwable != null) {
                    MqttLogger.error("Connect failed: ${throwable.message}")
                    onDisconnected?.invoke()

                    //jika gagal, coba lagi setelah delay dengan exponential backoff
                    if (!isIntentionallyStopped) scheduleRetry()
                    return@whenComplete
                }
                if (connAck?.returnCode == Mqtt3ConnAckReturnCode.SUCCESS) {
                    val protocol = if (config.useWebSocket) "ws" else "tcp"
                    MqttLogger.connect("$protocol://$host:$port", retryCount > 0)
                    retryCount = 0
                    onConnected?.invoke()

                    //Flush queue setelah koneksi berhasil
                    scope.launch {
                        queueManager.flush(this@MqttManager)
                    }
                } else {
                    MqttLogger.error("Connect rejected: ${connAck?.returnCode}")
                    onDisconnected?.invoke()

                    //jika ditolak, coba lagi setelah delay dengan exponential backoff
                    if (!isIntentionallyStopped) scheduleRetry()
                }
            }
    }

    private fun publish(topic: String, json: String, qos: Int) {
        if (!isConnected()) {

            scope.launch {
                queueManager.save(topic, json)
            }

            Log.d("MQTT_QUEUE", "Saved to queue DB")
            onPublishFailed?.invoke(topic, "Queued (offline)")
            return
        }
        val mqttQos = if (qos >= 2) MqttQos.EXACTLY_ONCE else MqttQos.AT_LEAST_ONCE

        client?.publishWith()
            ?.topic(topic)
            ?.qos(mqttQos)
            ?.payload(json.toByteArray(StandardCharsets.UTF_8))
            ?.retain(false)
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable != null) {
                    MqttLogger.error("Publish failed ($topic): ${throwable.message}")
                    onPublishFailed?.invoke(topic, throwable.message ?: "Unknown error")
                } else {
                    MqttLogger.publish(topic, json)
                    onPublishSuccess?.invoke(topic)
                }
            }
    }

    private fun scheduleRetry() {
        if (retryCount >= MAX_RETRY) {
            MqttLogger.error("Max retry reached ($MAX_RETRY). Giving up.")
            return
        }
        retryJob?.cancel()
        retryJob = scope.launch {
            retryCount++
            val delayMs = RETRY_DELAY_MS * retryCount
            Log.i(TAG, "Retry #$retryCount in ${delayMs / 1000}s...")
            delay(delayMs)
            if (!isIntentionallyStopped) doConnect()
        }
    }

    private fun buildRadioDataJson(p: RadioDataPayload): String {
        return JSONObject().apply {

            put("timestamp", p.timestamp)
            put("serial_number", p.serialNumber)
            put("android_id", p.androidId)
            put("app_version", p.appVersion)

            put("identity", JSONObject().apply {
                put("id", p.identity.id)
                put("nrp", p.identity.nrp)
                put("name", p.identity.name)
                put("rank", p.identity.rank)
                put("unit", p.identity.unit)
                put("battalion", p.identity.battalion)
                put("squad", p.identity.squad)
                put("avatar_url", p.identity.avatarUrl)
            })

            put("gps", JSONObject().apply {
                put("gps_timestamp", p.gps.gpsTimestamp)
                put("latitude", p.gps.latitude)
                put("longitude", p.gps.longitude)
                put("accuracy",p.gps.accuracy)
            })

            put("radio_health", JSONObject().apply {
                put("heartrate_timestamp", p.radioHealth.heartrateTimestamp)
                put("heartrate", p.radioHealth.heartrate)
            })

            put("battery", JSONObject().apply {
                put("battery_timestamp", p.battery.batteryTimestamp)
                put("level", p.battery.level)
            })

            put("stream", JSONObject().apply {
                put("rtmp_url", p.stream.rtmpUrl)
            })

        }.toString()
    }

    private fun buildRadioSosJson(p: RadioSosPayload): String {
        return JSONObject().apply {
            put("timestamp", p.timestamp)
            put("serial_number", p.serialNumber)
            put("android_id", p.androidId)
            put("id", p.id)
            put("name", p.name)
            put("avatar", p.avatarUrl)
            put("sos", p.sos)
            put("latitude", p.latitude)
            put("longitude", p.longitude)
            put("accuracy",p.accuracy)
        }.toString()
    }

    private fun buildBodycamDataJson(p: BodycamDataPayload): String {
        return JSONObject().apply {
            put("timestamp", p.timestamp)
            put("serial_number", p.serialNumber)
            put("android_id", p.androidId)
            put("stream_url", p.streamUrl)
        }.toString()
    }

    private fun buildBodycamSosJson(p: BodycamSosPayload): String {
        return JSONObject().apply {
            put("timestamp", p.timestamp)
            put("serial_number", p.serialNumber)
            put("android_id", p.androidId)
            put("sos", p.sos)
        }.toString()
    }
}