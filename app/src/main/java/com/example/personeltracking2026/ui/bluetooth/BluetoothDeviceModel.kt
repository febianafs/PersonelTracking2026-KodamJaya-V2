package com.example.personeltracking2026.ui.bluetooth

/**
 * Model data untuk setiap perangkat Bluetooth yang ditemukan saat scanning.
 */
data class BluetoothDeviceModel(
    val name: String,
    val address: String,   // MAC address, e.g. "AA:BB:CC:11:22:33"
    val rssi: Int,         // Signal strength, e.g. -62
    var state: DeviceState = DeviceState.IDLE
)

enum class DeviceState {
    IDLE,         // Belum diapa-apain → tampilkan tombol "Hubungkan"
    CONNECTING,   // Sedang proses → tampilkan "Menghubungkan..."
    CONNECTED     // Sudah terhubung → tampilkan badge "Terhubung" + "Lepaskan"
}