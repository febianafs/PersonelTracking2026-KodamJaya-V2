package com.example.personeltracking2026kodamjayav2.core.mqtt

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.personeltracking2026kodamjayav2.core.service.MqttLocationService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        MqttLocationService.startService(ctx)
    }
}
