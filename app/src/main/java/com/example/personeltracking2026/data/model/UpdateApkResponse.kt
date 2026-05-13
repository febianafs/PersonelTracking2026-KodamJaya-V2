package com.example.personeltracking2026.data.model

data class UpdateApkResponse(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String
)