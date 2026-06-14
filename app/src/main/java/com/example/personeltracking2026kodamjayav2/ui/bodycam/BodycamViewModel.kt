package com.example.personeltracking2026kodamjayav2.ui.bodycam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.personeltracking2026kodamjayav2.data.repository.BodycamRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BodycamViewModel(
    private val repository: BodycamRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : ViewModel() {

    // ─── Stream state ───
    private val _streamState = MutableStateFlow<StreamState>(StreamState.Idle)
    val streamState: StateFlow<StreamState> = _streamState

    // ─── Timer ───
    private val _timerText = MutableStateFlow("00:00")
    val timerText: StateFlow<String> = _timerText

    // ─── Resolusi: false = SD 480p, true = HD 720p ───
    private val _isHdSelected = MutableStateFlow(false)
    val isHdSelected: StateFlow<Boolean> = _isHdSelected

    private var timerJob: Job? = null
    private var elapsedSeconds = 0

    // ─────────────────────────────────────────────
    //  Stream control
    // ─────────────────────────────────────────────

    fun startStream() {
        _streamState.value = StreamState.Live
        startTimer()
    }

    fun stopStream() {
        timerJob?.cancel()
        val duration = _timerText.value
        _streamState.value = StreamState.Ended(duration = duration)
        elapsedSeconds = 0
        _timerText.value = "00:00"
    }

    fun saveRecording() {
        repository.saveRecording(_timerText.value)
        resetStream()
    }

    fun discardRecording() {
        repository.discardRecording()
        resetStream()
    }

    fun resetStream() {
        _streamState.value = StreamState.Idle
    }

    // ─────────────────────────────────────────────
    //  Helper
    // ─────────────────────────────────────────────

    /** Cek apakah sedang live — dipakai Activity untuk cegah ubah resolusi saat live */
    fun isLive(): Boolean = _streamState.value is StreamState.Live

    /** Set resolusi: false = SD · 480p, true = HD · 720p */
    fun setResolution(isHd: Boolean) {
        if (!isLive()) _isHdSelected.value = isHd
    }

    // ─────────────────────────────────────────────
    //  Timer
    // ─────────────────────────────────────────────

    private fun startTimer() {
        timerJob?.cancel()
        elapsedSeconds = 0
        timerJob = viewModelScope.launch(dispatcher) {
            while (true) {
                delay(1000)
                elapsedSeconds++
                val minutes = elapsedSeconds / 60
                val seconds = elapsedSeconds % 60
                _timerText.value = String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    // ─────────────────────────────────────────────
    //  Factory
    // ─────────────────────────────────────────────

    class Factory(
        private val repository: BodycamRepository,
        private val dispatcher: CoroutineDispatcher = Dispatchers.Main
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BodycamViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return BodycamViewModel(repository, dispatcher) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}