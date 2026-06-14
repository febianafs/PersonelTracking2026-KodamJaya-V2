package com.example.personeltracking2026kodamjayav2.data.repository

import android.content.Context
import com.example.personeltracking2026kodamjayav2.core.location.AppLocationManager
import com.example.personeltracking2026kodamjayav2.data.model.LocationData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationRepository(private val context: Context) {

    fun getLocationFlow(intervalMs: Long = 5000L): Flow<kotlin.Result<LocationData>> = callbackFlow {
        val manager = AppLocationManager(context)
        manager.setInterval(intervalMs)

        manager.onLocationUpdate = { lat, lon, accuracy, source ->
            trySend(kotlin.Result.success(LocationData(lat, lon, accuracy, source)))
        }
        manager.onLocationError = { message ->
            trySend(kotlin.Result.failure(Exception(message)))
        }

        manager.startUpdates()

        awaitClose { manager.stopUpdates() }
    }
}