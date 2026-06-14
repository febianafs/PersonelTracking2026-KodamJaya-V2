package com.example.personeltracking2026kodamjayav2.core.mqtt.queue

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MqttMessageEntity::class], version = 1)
abstract class MqttDatabase : RoomDatabase() {

    abstract fun mqttDao(): MqttDao

    companion object {
        @Volatile private var INSTANCE: MqttDatabase? = null

        fun getInstance(context: Context): MqttDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context,
                    MqttDatabase::class.java,
                    "mqtt_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}