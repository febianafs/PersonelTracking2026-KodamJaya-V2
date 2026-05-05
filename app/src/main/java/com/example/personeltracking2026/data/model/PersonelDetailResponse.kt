package com.example.personeltracking2026.data.model

data class PersonelDetailResponse(
    val code: Int,
    val data: PersonelData?,
    val message: String,
    val status: String
)

data class PersonelData(
    val id: Int,
    val nrp: String?,
    val email: String?,
    val avatar_url: String?,
    val batalyon: UnitItem?,
    val brigade: UnitItem?,
    val client: UnitItem?,
    val divisi: UnitItem?,
    val kompi: UnitItem?,
    val peleton: UnitItem?,
    val rank: UnitItem?,
    val regu: UnitItem?,
    val satuan: UnitItem?,
    val team: UnitItem?,
    val unit: UnitItem?
)

data class UnitItem(
    val id: Int,
    val name: String?
)
