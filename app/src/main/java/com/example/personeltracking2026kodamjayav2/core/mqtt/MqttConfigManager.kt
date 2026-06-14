package com.example.personeltracking2026kodamjayav2.core.mqtt

import android.content.Context

class MqttConfigManager(context: Context) {

    private val prefs = context.getSharedPreferences("mqtt_config", Context.MODE_PRIVATE)

    companion object {
        private const val DEFAULT_HOST = "147.139.161.159"
        private const val DEFAULT_TCP_PORT = 1883
        private const val DEFAULT_WS_PORT = 9001
        private const val DEFAULT_USERNAME = "kodam"
        private const val DEFAULT_PASSWORD = "kodam2025"
        private const val CONFIG_VERSION = 1
    }

    fun save(config: MqttConfig) {
        prefs.edit()
            .putString("host",     config.host)
            .putInt("tcp_port",    config.tcpPort)
            .putInt("ws_port",     config.wsPort)
            .putString("username", config.username)
            .putString("password", config.password)
            .putBoolean("use_ws",  config.useWebSocket)
            .apply()
    }

    fun load(): MqttConfig {
        migrateToCurrentConfig()

        return MqttConfig(
            host         = prefs.getString("host", DEFAULT_HOST) ?: DEFAULT_HOST,
            tcpPort      = prefs.getInt("tcp_port", DEFAULT_TCP_PORT),
            wsPort       = prefs.getInt("ws_port", DEFAULT_WS_PORT),
            username     = prefs.getString("username", DEFAULT_USERNAME) ?: DEFAULT_USERNAME,
            password     = prefs.getString("password", DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD,
            useWebSocket = prefs.getBoolean("use_ws",  true)
        )
    }

    private fun migrateToCurrentConfig() {
        if (prefs.getInt("config_version", 0) >= CONFIG_VERSION) return

        prefs.edit()
            .putString("host", DEFAULT_HOST)
            .putInt("ws_port", DEFAULT_WS_PORT)
            .putString("username", DEFAULT_USERNAME)
            .putString("password", DEFAULT_PASSWORD)
            .putBoolean("use_ws", true)
            .putInt("config_version", CONFIG_VERSION)
            .apply()
    }
}
