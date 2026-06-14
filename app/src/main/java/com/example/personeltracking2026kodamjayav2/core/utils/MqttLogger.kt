package com.example.personeltracking2026kodamjayav2.core.utils

import android.util.Log

object MqttLogger {

    private const val TAG_PUB = "MQTT_PUBLISH"
    private const val TAG_SUB = "MQTT_RECEIVE"
    private const val TAG_CONN = "MQTT_CONN"
    private const val TAG_ERR = "MQTT_ERROR"

    fun publish(topic: String, payload: String) {
        Log.d(TAG_PUB, "[${System.currentTimeMillis()}] [$topic] -> $payload")
    }

    fun receive(topic: String, payload: String) {
        Log.d(TAG_SUB, "[${System.currentTimeMillis()}] [$topic] -> $payload")
    }

    fun connect(server: String?, reconnect: Boolean) {
        Log.d(TAG_CONN, "Connected to $server | reconnect=$reconnect")
    }

    fun error(message: String) {
        Log.e(TAG_ERR, message)
    }
}