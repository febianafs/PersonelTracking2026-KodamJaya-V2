package com.example.personeltracking2026kodamjayav2.core.network

sealed class NetworkState {
    object Connected : NetworkState()
    object Connecting : NetworkState()
} 