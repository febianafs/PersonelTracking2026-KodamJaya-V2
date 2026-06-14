package com.example.personeltracking2026kodamjayav2.data.model

data class LoginResponse(
    val code: Int,
    val data: LoginData?,
    val message: String,
    val status: String
)

data class LoginData(
    val profile: ProfileData?,
    val client: ClientData?,
    val user: UserData?,
    val token: String?,
    val path_access_roles: List<String>?
)

data class ProfileData(
    val id: Int?,
    val user_id: Int?,
    val nrp: String?,
    val full_name: String?,
    val phone_number: String?,
    val date_of_birth: String?,
    val avatar_url: String?,
    val created_at: String?,
    val updated_at: String?,
    val classification: List<ClassificationItem>?
)

data class ClientData(
    val name: String?,
    val amount_of_personel_data: Int?,
    val url_image: String?,
    val desc: String?
)

data class UserData(
    val id: Int?,
    val name: String?,
    val email: String?,
    val username: String?,
    val client_id: Int?,
    val is_moderator: Boolean?,
    val roles: List<RoleData>?
)

data class RoleData(
    val id: Int?,
    val name: String?,
    val alias: String?
)

data class SimpleNameData(
    val id: Int? = null,
    val name: String?
)

data class ClassificationItem(
    val label: String?,
    val value: String?
)

fun ProfileData?.getClassification(label: String): String {

    return this?.classification
        ?.firstOrNull {
            it.label.equals(label, ignoreCase = true)
        }
        ?.value
        ?: ""
}