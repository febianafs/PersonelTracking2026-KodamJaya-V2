package com.example.personeltracking2026kodamjayav2.core.mqtt

data class MqttTopicConfig(
    val personelDataTopic: String,
    val personelSosTopic: String,
    val bodycamDataTopic: String,
    val bodycamSosTopic: String
)
