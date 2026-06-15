package com.example.personeltracking2026kodamjayav2.utils

object StreamUtils {

    fun getRtmpUrl(serial: String): String {
        return "rtmp://147.139.161.159:22935/personel/$serial"
    }
}
