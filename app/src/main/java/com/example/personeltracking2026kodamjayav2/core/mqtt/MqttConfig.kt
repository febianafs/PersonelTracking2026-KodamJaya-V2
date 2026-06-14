package com.example.personeltracking2026kodamjayav2.core.mqtt

data class MqttConfig(
    val host: String,
    val tcpPort: Int,
    val wsPort: Int,
    val username: String,
    val password: String,
    val useWebSocket: Boolean
)