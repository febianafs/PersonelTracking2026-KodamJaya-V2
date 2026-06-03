package com.example.personeltracking2026.data.repository

import com.example.personeltracking2026.core.network.RetrofitClient
import com.example.personeltracking2026.data.model.LoginRequest
import com.example.personeltracking2026.data.model.LoginResponse

class LoginRepository {
    private val api = RetrofitClient.instance

    suspend fun login(username: String, password: String): Result<LoginResponse> {
        return try {
            val response = api.login(LoginRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                val errorMsg = when (response.code()) {
                    400 -> "Invalid request"
                    401 -> "Invalid email or password"
                    else -> "An error occurred: ${response.code()}"
                }
                Result.Error(errorMsg)
            }
        } catch (e: Exception) {
            Result.Error("Network unavailable. Please check your connection.")
        }
    }
}