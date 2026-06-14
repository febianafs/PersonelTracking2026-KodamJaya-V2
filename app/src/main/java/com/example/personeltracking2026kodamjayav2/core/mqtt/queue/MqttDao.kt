package com.example.personeltracking2026kodamjayav2.core.mqtt.queue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
@Dao
interface MqttDao {

    @Insert
    suspend fun insert(msg: MqttMessageEntity)

    @Query("SELECT * FROM mqtt_queue ORDER BY id ASC")
    suspend fun getAll(): List<MqttMessageEntity>

    @Query("DELETE FROM mqtt_queue")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM mqtt_queue")
    suspend fun count(): Int

    @Query("DELETE FROM mqtt_queue WHERE id IN (SELECT id FROM mqtt_queue ORDER BY id ASC LIMIT 1)")
    suspend fun deleteOldest()
}