package com.example.personeltracking2026.core.mqtt

import android.content.Context

class MqttTopicManager(context: Context) {

    private val prefs =
        context.getSharedPreferences("mqtt_topics", Context.MODE_PRIVATE)

    fun save(config: MqttTopicConfig) {
        prefs.edit()
            .putString("personel_data", config.personelDataTopic)
            .putString("personel_sos", config.personelSosTopic)
            .putString("bodycam_data", config.bodycamDataTopic)
            .putString("bodycam_sos", config.bodycamSosTopic)
            .apply()
    }

    fun load(): MqttTopicConfig {

        return MqttTopicConfig(

            // DEFAULT TOPIC
            personelDataTopic =
                prefs.getString(
                    "personel_data",
                    "tjw/radio/data"
                ) ?: "tjw/radio/data",

            personelSosTopic =
                prefs.getString(
                    "personel_sos",
                    "tjw/radio/sos"
                ) ?: "tjw/radio/sos",

            bodycamDataTopic =
                prefs.getString(
                    "bodycam_data",
                    "tjw/bodycam/data"
                ) ?: "tjw/bodycam/data",

            bodycamSosTopic =
                prefs.getString(
                    "bodycam_sos",
                    "tjw/bodycam/sos"
                ) ?: "tjw/bodycam/sos"
        )
    }
}