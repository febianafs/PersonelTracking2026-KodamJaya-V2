package com.example.personeltracking2026kodamjayav2.ui.bodycam

sealed class StreamState {
    object Idle : StreamState()
    object Live : StreamState()
    data class Ended(val duration: String) : StreamState()
}