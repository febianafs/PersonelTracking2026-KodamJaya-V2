package com.example.personeltracking2026.data.model

data class LoginResponse(
    val code: Int,
    val data: LoginData?,
    val message: String,
    val status: String
)

data class LoginData(
    val profile: ProfileData?,
    val token: String,
    val user: UserData
)

data class ProfileData(
    val id: Int?,
    val user_id: Int?,
    val full_name: String?,
    val phone_number: String?,
    val date_of_birth: String?,
    val avatar_url: String?
)

data class UserData(
    val name: String,
    val roles: List<RoleData>
)

data class RoleData(
    val id: Int,
    val name: String
)