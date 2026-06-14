package com.example.personeltracking2026kodamjayav2.core.sos

import android.util.Log
import com.example.personeltracking2026kodamjayav2.core.mqtt.MqttManager
import com.example.personeltracking2026kodamjayav2.core.mqtt.MqttPayloadBuilder
import com.example.personeltracking2026kodamjayav2.core.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object SosManager {
    private const val TAG = "SosManager"

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    enum class DeviceType {
        RADIO,
        BODYCAM
    }

    private var mqttManager: MqttManager? = null
    private var sessionManager: SessionManager? = null
    private var serialNumber: String = "unknown"
    private var androidId: String = "unknown"
    private var deviceType: DeviceType = DeviceType.RADIO
    private var getLocation: (() -> Triple<Double, Double, Float>)? = null

    fun init(
        mqtt: MqttManager,
        session: SessionManager,
        serial: String,
        id: String,
        type: DeviceType,
        locationProvider: () -> Triple<Double, Double, Float>
    ) {
        mqttManager    = mqtt
        sessionManager = session
        serialNumber   = serial
        androidId      = id
        deviceType     = type
        getLocation    = locationProvider

        mqtt.onSosMessageReceived = { topic, payload ->
            applySubscribedSosPayload(topic, payload)
        }
        mqtt.setSosSubscriptionSelector { topics ->
            listOf(topics.personelSosTopic)
        }
    }

    fun activate() { _isActive.value = true; publishSos(1) }
    fun deactivate() { _isActive.value = false; publishSos(0) }
    fun toggle() { if (_isActive.value) deactivate() else activate() }

    private fun applySubscribedSosPayload(topic: String, payload: String) {
        try {
            val json = JSONObject(payload)
            val payloadSerial = json.optString("serial_number", "").trim()
            val localSerial = serialNumber.trim()
            if (payloadSerial.isBlank() || localSerial.isBlank()) {
                Log.w(TAG, "Ignoring SOS payload without serial_number from $topic")
                return
            }
            if (payloadSerial == localSerial) {
                Log.d(TAG, "Ignoring own SOS payload serial_number=$payloadSerial")
                return
            }

            val payloadId = json.optString("id", "").trim()
            val localId = currentPayloadId()
            if (payloadId.isBlank() || localId.isBlank()) {
                Log.w(TAG, "Ignoring SOS payload without pair id from $topic")
                return
            }
            if (payloadId != localId) {
                Log.d(TAG, "Ignoring SOS payload id=$payloadId for local id=$localId")
                return
            }

            val sosValue = json.optInt("sos", -1)
            when (sosValue) {
                1 -> _isActive.value = true
                0 -> _isActive.value = false
                else -> {
                    Log.w(TAG, "Ignoring SOS payload without valid sos value from $topic")
                    return
                }
            }

            Log.d(TAG, "Applied remote SOS state=$sosValue from $topic")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse SOS payload from $topic: ${e.message}")
        }
    }

    private fun currentPayloadId(): String {
        val session = sessionManager ?: return ""
        return session.getUserId()
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: session.getId().trim()
    }

    private fun publishSos(sosValue: Int) {
        val mqtt    = mqttManager ?: return
        val session = sessionManager ?: return
        val (lat, lon, accuracy) = getLocation?.invoke() ?: return

        val payload = MqttPayloadBuilder.buildRadioSosPayload(
            session      = session,
            serialNumber = serialNumber,
            androidId    = androidId,
            lat          = lat,
            lon          = lon,
            acc          = accuracy,
            sos          = sosValue
        )
        mqtt.publishRadioSos(payload)
    }
}
