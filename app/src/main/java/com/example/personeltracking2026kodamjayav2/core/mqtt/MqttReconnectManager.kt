package com.example.personeltracking2026kodamjayav2.core.mqtt

import android.content.Context
import android.net.*
import android.util.Log
import kotlinx.coroutines.*

class MqttReconnectManager(
    private val context: Context,
    private val mqttManager: MqttManager
) {

    private val TAG = "MQTT_RECONNECT"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var watchdogJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isReconnecting = false

    // ─────────────────────────────
    // START SYSTEM
    // ─────────────────────────────
    fun start() {
        startNetworkMonitor()
        startWatchdog()
    }

    // ─────────────────────────────
    // STOP SYSTEM
    // ─────────────────────────────
    fun stop() {
        watchdogJob?.cancel()
        unregisterNetwork()
    }

    // ─────────────────────────────
    // NETWORK MONITOR
    // Hanya reconnect kalau MQTT benar-benar putus
    // ─────────────────────────────
    private fun startNetworkMonitor() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                val caps = (context.getSystemService(Context.CONNECTIVITY_SERVICE)
                        as ConnectivityManager).getNetworkCapabilities(network)

                val isValidated = caps?.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                ) == true

                if (!isValidated) {
                    Log.d(TAG, "Network not validated → skip")
                    return
                }

                // Hanya reconnect kalau memang putus
                if (!mqttManager.isConnected()) {
                    Log.d(TAG, "Network available + MQTT disconnected → reconnect")
                    reconnectNow()
                } else {
                    Log.d(TAG, "Network available + MQTT already connected → skip")
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
            }
        }

        cm.registerNetworkCallback(request, networkCallback!!)
    }

    private fun unregisterNetwork() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
    }

    // ─────────────────────────────
    // WATCHDOG — cek tiap 30 detik
    // Hanya connect kalau putus, JANGAN disconnect dulu
    // ─────────────────────────────
    private fun startWatchdog() {
        watchdogJob = scope.launch {
            while (isActive) {
                delay(30_000) // cek tiap 30 detik, bukan 5 detik

                if (!mqttManager.isConnected()) {
                    Log.d(TAG, "Watchdog: MQTT not connected → reconnect")
                    reconnectNow()
                }
                // kalau sudah connected, tidak lakukan apa-apa
            }
        }
    }

    // ─────────────────────────────
    // RECONNECT
    // TIDAK disconnect dulu — langsung connect
    // MqttManager akan handle kalau client sudah ada
    // ─────────────────────────────
    private fun reconnectNow() {
        if (isReconnecting) return
        isReconnecting = true

        scope.launch {
            try {
                // JANGAN disconnect dulu — hanya connect
                // kalau memang sudah disconnected dari broker
                mqttManager.connect()
                Log.d(TAG, "Reconnect triggered")
            } catch (e: Exception) {
                Log.e(TAG, "Reconnect error: ${e.message}")
            } finally {
                // Beri jeda sebelum boleh reconnect lagi
                delay(10_000)
                isReconnecting = false
            }
        }
    }
}