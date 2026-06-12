package com.example.personeltracking2026.core.mqtt.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.personeltracking2026.core.mqtt.MqttManager

class MqttWatchdogWorker(ctx: Context, params: WorkerParameters)
    : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {

        val mqtt = MqttManager(applicationContext)

        if (!mqtt.isConnected()) {
            mqtt.connect()
        }

        return Result.success()
    }
}
