package com.example.personeltracking2026kodamjayav2.core.mqtt.queue

import android.content.Context
import com.example.personeltracking2026kodamjayav2.core.mqtt.MqttManager

class MqttQueueManager(context: Context) {

    private val dao = MqttDatabase.getInstance(context).mqttDao()

    private val MAX_QUEUE_SIZE = 200

    suspend fun save(topic: String, payload: String) {

        val currentSize = dao.count()

        // LIMIT QUEUE
        if (currentSize >= MAX_QUEUE_SIZE) {
            dao.deleteOldest()
        }

        dao.insert(
            MqttMessageEntity(
                topic = topic,
                payload = payload,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun flush(mqttManager: MqttManager) {

        val list = dao.getAll()

        list.forEach {
            mqttManager.publishDirect(it.topic, it.payload)
        }

        dao.clear()
    }
}