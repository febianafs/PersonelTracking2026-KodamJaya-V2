package com.example.personeltracking2026kodamjayav2.data.model

data class AboutResponse(
    val code: Int,
    val data: AboutData?,
    val message: String,
    val status: String
)

data class AboutData(
    val app_code: String?,
    val app_name: String?,
    val company_name: String?,
    val copyright_text: String?,
    val dev: String?,
    val legal_notice: String?,
    val privacy_policy_url: String?
)