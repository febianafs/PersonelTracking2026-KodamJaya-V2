package com.example.personeltracking2026.ui.personel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personeltracking2026.App
import com.example.personeltracking2026.BuildConfig
import com.example.personeltracking2026.core.mqtt.MqttPayloadBuilder
import com.example.personeltracking2026.core.session.SessionManager
import com.example.personeltracking2026.data.model.LocationData
import com.example.personeltracking2026.data.model.PersonelData
import com.example.personeltracking2026.data.model.RadioDataPayload
import com.example.personeltracking2026.data.repository.LocationRepository
import com.example.personeltracking2026.data.repository.PersonelRepository
import com.example.personeltracking2026.data.repository.Result
import com.example.personeltracking2026.utils.DeviceIdentityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

data class LocationState(
    val data       : LocationData? = null,
    val gpsStrength: Int           = 0,
    val isInZone   : Boolean       = false,
    val error      : String?       = null
)

data class BatteryState(
    val percent   : Int     = 0,
    val isCharging: Boolean = false
)

data class HeartRateState(
    val bpm        : Int     = 0,
    val timestamp  : Long    = 0L,
    val isConnected: Boolean = false,
    val deviceName : String  = ""
)

class PersonelViewModel(
    application           : Application,
    private val repository        : PersonelRepository,
    private val locationRepository: LocationRepository,
    private val sessionManager    : SessionManager
) : AndroidViewModel(application) {

    // LOG CSV START
    private val rawFile by lazy {
        File(
            getApplication<Application>().getExternalFilesDir(null),
            "gps_filtered.csv"
        ).apply {
            if (!exists()) {
                createNewFile()
                writeText("timestamp,lat,lon,accuracy\n")
            }
        }
    }
    private val publishFile by lazy {
        File(
            getApplication<Application>().getExternalFilesDir(null),
            "gps_publish.csv"
        ).apply {
            if (!exists()) {
                createNewFile()
                writeText("timestamp,lat,lon,accuracy\n")
            }
        }
    }

    fun saveFilteredCsv(loc: LocationData) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileWriter(rawFile, true).use { writer ->
                    writer.append("${loc.timestamp},${loc.lat},${loc.lon},${loc.accuracy}\n")
                }
            } catch (e: Exception) {
                Log.e("CSV_FILTERED", "Error", e)
            }
        }
    }

    fun savePublishCsv(loc: LocationData) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileWriter(publishFile, true).use { writer ->
                    writer.append("${loc.timestamp},${loc.lat},${loc.lon},${loc.accuracy}\n")
                }
            } catch (e: Exception) {
                Log.e("CSV_PUBLISH", "Error", e)
            }
        }
    }
    // LOG CSV END

    companion object {
        private const val ZONE_CENTER_LAT    = -7.868729
        private const val ZONE_CENTER_LON    = 105.643117
        private const val ZONE_RADIUS_METERS = 500.0
    }

    private var locationJob: Job? = null
    private var lastLocation: LocationData? = null
    private var publishJob: Job? = null
    private val intervalFlow = MutableStateFlow(5000L)
    private var lastAccepted: LocationData? = null
    private val smoothWindow = ArrayDeque<LocationData>()

    // ─── STATE FLOWS ─────────────────────────────────────────────────────────

    private val _personelState  = MutableStateFlow<PersonelState>(PersonelState.Loading)
    val personelState : StateFlow<PersonelState> = _personelState

    private val _locationState  = MutableStateFlow(LocationState())
    val locationState : StateFlow<LocationState> = _locationState.asStateFlow()

    private val _batteryState   = MutableStateFlow(BatteryState())
    val batteryState  : StateFlow<BatteryState> = _batteryState.asStateFlow()

    private val _mqttConnected  = MutableStateFlow(false)
    val mqttConnected : StateFlow<Boolean> = _mqttConnected.asStateFlow()

    private val _lastSyncTime   = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTime  : StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _heartRateState = MutableStateFlow(HeartRateState())
    val heartRateState: StateFlow<HeartRateState> = _heartRateState.asStateFlow()

    // ─── MQTT ────────────────────────────────────────────────────────────────

    val mqttManager = (application as App).mqttManager.apply {
        onConnected      = { _mqttConnected.value = true }
        onDisconnected   = { _mqttConnected.value = false }
        onPublishSuccess = {
            val now = System.currentTimeMillis()
            Log.d("MQTT_TIMER", "SUCCESS publish at $now")
            _lastSyncTime.value = now
        }
    }

    // ─── BATTERY RECEIVER ────────────────────────────────────────────────────

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModelScope.launch(Dispatchers.IO) { refreshBattery() }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) { refreshBattery() }
    }

    override fun onCleared() {
        super.onCleared()
    }

    // ─── BATTERY RECEIVER LIFECYCLE ──────────────────────────────────────────

    /**
     * Dipanggil dari Activity.onStart()
     */
    fun registerBatteryReceiver(context: Context) {
        context.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
    }

    /**
     * Dipanggil dari Activity.onStop()
     */
    fun unregisterBatteryReceiver(context: Context) {
        try { context.unregisterReceiver(batteryReceiver) }
        catch (e: Exception) { /* abaikan jika belum terdaftar */ }
    }

    // ─── PERSONEL ────────────────────────────────────────────────────────────

    fun loadPersonelDetail(userId: Int, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _personelState.value = PersonelState.Loading
            }
            when (val result = repository.getPersonelDetail(userId, token)) {
                is Result.Success -> {
                    val data = result.data
                    // Simpan detail ke session supaya tersedia meski API gagal berikutnya
                    sessionManager.savePersonelDetail(
                        nrp       = data.nrp,
                        rank      = data.rank?.name,
                        unit      = data.unit?.name,
                        battalion = data.batalyon?.name,
                        squad     = data.regu?.name,
                        avatar    = data.image
                    )
                    withContext(Dispatchers.Main) {
                        _personelState.value = PersonelState.Success(data)
                    }
                }
                is Result.Error -> withContext(Dispatchers.Main) {
                    _personelState.value = PersonelState.Error(result.message)
                }
                else -> {}
            }
        }
    }

    // ─── LOCATION & MQTT PUBLISH ─────────────────────────────────────────────

    fun startLocationUpdates(intervalMs: Long = 5000L) {
        locationJob?.cancel()

        locationJob = viewModelScope.launch {
            locationRepository.getLocationFlow(intervalMs).collect { kotlinResult ->
                val locationData = kotlinResult.getOrNull()
                val error        = kotlinResult.exceptionOrNull()

                if (locationData != null) {publishJob
                    val filteredLoc = processLocation(locationData) ?: return@collect

                    _locationState.value = LocationState(
                        data        = filteredLoc,
                        gpsStrength = accuracyToStrength(filteredLoc.accuracy),
                        isInZone    = checkInZone(filteredLoc.lat, filteredLoc.lon)
                    )
                    val app = getApplication<Application>() as App
                    app.currentLat = filteredLoc.lat
                    app.currentLon = filteredLoc.lon

                    // --- INPUT CSV ---
                    //saveFilteredCsv(filteredLoc)

                    // Simpan last location
                    lastLocation = filteredLoc

                } else if (error != null) {
                    _locationState.update { it.copy(error = error.message, gpsStrength = 0) }
                }
            }
        }
    }

    fun startPublishing() {
        publishJob?.cancel()

        publishJob = viewModelScope.launch {
            intervalFlow.collectLatest { interval ->

                Log.d("MQTT_TIMER", "NEW INTERVAL = $interval ms")

                while (true) {
                    val now = System.currentTimeMillis()

                    lastLocation?.let { location ->
                        val now = System.currentTimeMillis()

                        if (mqttManager.isConnected()) {
                            Log.d("MQTT_TIMER", "PUBLISH at $now")

                            _lastSyncTime.value = now

                            withContext(Dispatchers.IO) {
                                publishDataPayload(location)
                            }
                        } else {
                            Log.d("MQTT_TIMER", "MQTT NOT CONNECTED → SKIP")
                        }
                    }

                    delay(interval)
                }
            }
        }
    }

    fun updateInterval(intervalMs: Long) {
        intervalFlow.value = intervalMs
    }

    private fun publishDataPayload(location: LocationData) {
        if (!mqttManager.isConnected()) {
            Log.d("MQTT_TIMER", "MQTT NOT CONNECTED → SKIP")
            return
        }

        val deviceManager = DeviceIdentityManager(getApplication())
        val identity = deviceManager.getIdentity()

        if (identity == null) {
            Log.e("MQTT", "Serial belum di-set")
            return
        }

        val serialNumber = identity.serial
        val androidId    = identity.androidId

        val hr  = _heartRateState.value
        val bat = _batteryState.value

        val payload = MqttPayloadBuilder.buildRadioDataPayload(
            session      = sessionManager,
            serialNumber = serialNumber,
            androidId    = androidId,
            lat          = location.lat,
            lon          = location.lon,
            acc          = location.accuracy,
            gpsTimestamp = location.timestamp,
            heartrate    = hr.bpm,
            heartrateTs  = if (hr.timestamp > 0) hr.timestamp else System.currentTimeMillis(),
            batteryLevel = bat.percent,
            appVersion   = BuildConfig.APP_VERSION,
            rtmpUrl      = StreamUtils.getRtmpUrl(serialNumber)
        )
        // --- INPUT CSV ---
        //savePublishCsv(location)
        //Log.d("MQTT_TIMER", "SEND PAYLOAD = $payload")
        Log.d("GPS_TEST", "time=${location.timestamp}, lat=${location.lat}, lon=${location.lon}")
        mqttManager.publishRadioData(payload)
        RadioDataPayload::class.java.declaredFields.forEach {
            Log.d("FIELDS", it.name)
        }
    }

    private fun processLocation(newLoc: LocationData): LocationData? {

        // 1. FILTER ACCURACY
        if (newLoc.accuracy > 20f) return null
        val last = lastAccepted
        if (last == null) {
            lastAccepted = newLoc
            return newLoc
        }

        val dt = ((newLoc.timestamp - last.timestamp)/ 1000f).coerceAtLeast(0.5f)
        val dist = distance(last, newLoc)
        val moving = isMoving(newLoc, last)
        if (moving) {
            smoothWindow.clear()
        }

        // --- OUTLIER FILTER ---
        val maxJump = 8f + 2f * dt
        if (dist > maxJump) {
            Log.d("FILTER", "OUTLIER: $dist")
            return null
        }

        // 2. DYNAMIC DISTANCE FILTER
        val smallMove = 3f + dt
        if (!moving && dist < smallMove) {
            val tau = 6f
            val alpha = dt / (tau + dt)
            val blendedLat = last.lat + alpha * (newLoc.lat - last.lat)
            val blendedLon = last.lon + alpha * (newLoc.lon - last.lon)

            val blended = newLoc.copy(lat = blendedLat, lon = blendedLon)
            val finalLoc = smooth(blended, dt)

            lastAccepted = finalLoc
            return finalLoc
        }

        // 3. BLENDING + SMOOTHING
        val alpha = if (moving) {
            when {
                newLoc.accuracy < 5 -> 0.7f
                newLoc.accuracy < 10 -> 0.5f
                else -> 0.3f
            }
        } else {
            dt / (5f + dt)
        }
        val blendedLat = last.lat + alpha * (newLoc.lat - last.lat)
        val blendedLon = last.lon + alpha * (newLoc.lon - last.lon)
        val blended = newLoc.copy(
            lat = blendedLat,
            lon = blendedLon
        )
        val finalLoc = if (!moving) {
            smooth(blended, dt)
        } else {
            blended
        }
        lastAccepted = finalLoc
        return finalLoc
    }

    fun publishSos(sosValue: Int) {
        val loc = _locationState.value.data ?: return

        val deviceManager = DeviceIdentityManager(getApplication())
        val identity = deviceManager.getIdentity()

        if (identity == null) {
            Log.e("MQTT", "Serial belum di-set")
            return
        }

        val serialNumber = identity.serial
        val androidId    = identity.androidId

        val payload = MqttPayloadBuilder.buildRadioSosPayload(
            session      = sessionManager,
            serialNumber = serialNumber,
            androidId    = androidId,
            lat          = loc.lat,
            lon          = loc.lon,
            acc          = loc.accuracy,
            sos          = sosValue
        )
        mqttManager.publishRadioSos(payload)
    }

    // ─── HEART RATE (BLE) ────────────────────────────────────────────────────

    fun updateHeartRate(bpm: Int, deviceName: String = "") {
        _heartRateState.update {
            it.copy(
                bpm        = bpm,
                timestamp  = System.currentTimeMillis(),
                deviceName = deviceName.ifEmpty { it.deviceName }
            )
        }

        val app = getApplication<Application>()
        if (app is com.example.personeltracking2026.App) {
            app.currentHeartRate   = bpm
            app.currentHeartRateTs = System.currentTimeMillis()
        }
    }

    fun onBleConnected(deviceName: String) {
        _heartRateState.update { it.copy(isConnected = true, deviceName = deviceName) }
    }

    fun onBleDisconnected() {
        _heartRateState.update { it.copy(isConnected = false) }
    }

    // ─── BATTERY ─────────────────────────────────────────────────────────────

    suspend fun refreshBattery() = withContext(Dispatchers.IO) {
        val bm = getApplication<Application>()
            .getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent    = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        if (percent > 0) {
            withContext(Dispatchers.Main) {
                _batteryState.value = BatteryState(percent, isCharging)
            }
        }
    }

    // ─── LEGACY ──────────────────────────────────────────────────────────────

    fun onMqttPublishSuccess() {
        _lastSyncTime.value = System.currentTimeMillis()
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private fun accuracyToStrength(accuracy: Float) = when {
        accuracy <= 10f  -> 95
        accuracy <= 20f  -> 80
        accuracy <= 50f  -> 60
        accuracy <= 100f -> 40
        else             -> 20
    }

    private fun checkInZone(lat: Double, lon: Double): Boolean {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            lat, lon, ZONE_CENTER_LAT, ZONE_CENTER_LON, results
        )
        return results[0] <= ZONE_RADIUS_METERS
    }

    // HELPER HITUNG JARAK
    private fun distance(a: LocationData, b: LocationData): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            a.lat, a.lon,
            b.lat, b.lon,
            results
        )
        return results[0]
    }

    // HELPER DETEKSI GERAK
    private fun isMoving(newLoc: LocationData, last: LocationData?): Boolean {
        if (last == null) return true

        val dist = distance(last, newLoc)

        // pastikan timestamp tidak mundur
        val dtMillis = newLoc.timestamp - last.timestamp
        if (dtMillis <= 0) return false
        val timeSec = dtMillis / 1000f

        // hindari kasus delay terlalu lama
        if (timeSec > 10f) {
            return dist > 5f
        }
        val speed = dist / timeSec

        // kombinasi speed + jarak minimum
        return speed > 1.5f || dist > 8f
    }

    // HELPER SMOOTHING
    private fun smooth(loc: LocationData, dt: Float): LocationData {
        val maxWindow = (5f / dt).toInt().coerceIn(5, 15)
        smoothWindow.addLast(loc)
        if (smoothWindow.size > maxWindow) {
            smoothWindow.removeFirst()
        }

        val avgLat = smoothWindow.map { it.lat }.average()
        val avgLon = smoothWindow.map { it.lon }.average()

        return loc.copy(
            lat = avgLat,
            lon = avgLon
        )
    }

    // ─── FACTORY ─────────────────────────────────────────────────────────────

    class Factory(
        private val application       : Application,
        private val repository        : PersonelRepository,
        private val locationRepository: LocationRepository,
        private val sessionManager    : SessionManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PersonelViewModel(application, repository, locationRepository, sessionManager) as T
    }
}

sealed class PersonelState {
    object Loading : PersonelState()
    data class Success(val data: PersonelData) : PersonelState()
    data class Error(val message: String) : PersonelState()
}