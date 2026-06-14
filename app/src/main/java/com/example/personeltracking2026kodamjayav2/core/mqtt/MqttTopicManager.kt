package com.example.personeltracking2026kodamjayav2.core.mqtt

import android.content.Context

class MqttTopicManager(context: Context) {

    private val prefs =
        context.getSharedPreferences("mqtt_topics", Context.MODE_PRIVATE)

    companion object {
        private const val DATA_TOPIC = "kdm/radio/data"
        private const val SOS_TOPIC = "kdm/radio/sos"
        private const val TOPIC_VERSION = 1
    }

    fun save(config: MqttTopicConfig) {
        prefs.edit()
            .putString("personel_data", DATA_TOPIC)
            .putString("personel_sos", SOS_TOPIC)
            .putString("bodycam_data", DATA_TOPIC)
            .putString("bodycam_sos", SOS_TOPIC)
            .putInt("topic_version", TOPIC_VERSION)
            .apply()
    }

    fun load(): MqttTopicConfig {
        migrateTopics()
        return MqttTopicConfig(
            personelDataTopic = DATA_TOPIC,
            personelSosTopic = SOS_TOPIC,
            bodycamDataTopic = DATA_TOPIC,
            bodycamSosTopic = SOS_TOPIC
        )
    }

    private fun migrateTopics() {
        if (prefs.getInt("topic_version", 0) >= TOPIC_VERSION) return

        prefs.edit()
            .putString("personel_data", DATA_TOPIC)
            .putString("personel_sos", SOS_TOPIC)
            .putString("bodycam_data", DATA_TOPIC)
            .putString("bodycam_sos", SOS_TOPIC)
            .putInt("topic_version", TOPIC_VERSION)
            .apply()
    }
}
