package com.example.personeltracking2026.data.model

data class PersonelDetailResponse(
    val code: Int,
    val data: PersonelData?,
    val message: String,
    val status: String
)

data class PersonelData(
    val id: Int,
    val name: String?,
    val full_name: String?,
    val nrp: String?,
    val email: String?,
    val avatar_url: String?,
    val image: String?,
    val satuan: UnitItem?,
    val batalyon: UnitItem?,
    val pleton: UnitItem?,
    val peleton: UnitItem?,
    val kompi: UnitItem?,
    val brigade: UnitItem?,
    val divisi: UnitItem?,
    val team: UnitItem?,
    val unit: UnitItem?,
    val rank: UnitItem?,
    val regu: UnitItem?,
    val client: UnitItem?
)

data class UnitItem(
    val id: Int? = null,
    val name: String?
)
