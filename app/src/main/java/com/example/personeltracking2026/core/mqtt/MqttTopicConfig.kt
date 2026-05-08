package com.example.personeltracking2026.core.mqtt

data class MqttTopicConfig(
    val personelDataTopic: String,
    val personelSosTopic: String,
    val bodycamDataTopic: String,
    val bodycamSosTopic: String
)
